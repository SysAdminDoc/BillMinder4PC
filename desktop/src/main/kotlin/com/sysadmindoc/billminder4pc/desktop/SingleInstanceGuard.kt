package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owns the process-wide file lock and a loopback activation channel. A second process never opens
 * the database: it asks the lock owner to foreground its window and then returns from `main`.
 */
class SingleInstanceGuard private constructor(
    private val channel: FileChannel?,
    private val lock: FileLock?,
    private val server: ServerSocket?,
    private val endpointFile: Path?,
    private val token: String?,
    val isPrimary: Boolean
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activationChannel = Channel<Unit>(Channel.CONFLATED)

    val activationRequests: Flow<Unit> = activationChannel.receiveAsFlow()

    init {
        if (isPrimary) {
            val listener = requireNotNull(server)
            scope.launch {
                while (isActive && !listener.isClosed) {
                    try {
                        val socket = listener.accept()
                        socket.use { connection ->
                            connection.soTimeout = SOCKET_TIMEOUT_MILLIS
                            val suppliedToken = DataInputStream(connection.getInputStream()).readUTF()
                            val accepted = suppliedToken == token
                            if (accepted) activationChannel.trySend(Unit)
                            DataOutputStream(connection.getOutputStream()).apply {
                                writeBoolean(accepted)
                                flush()
                            }
                        }
                    } catch (_: IOException) {
                        if (listener.isClosed) break
                    }
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { server?.close() }
        activationChannel.close()

        if (endpointFile != null && token != null && readEndpoint(endpointFile)?.token == token) {
            runCatching { Files.deleteIfExists(endpointFile) }
        }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MILLIS = 1_000
        private const val RETRY_MILLIS = 50L

        fun acquire(
            lockFile: Path = AppPaths.instanceLockFile,
            endpointFile: Path = AppPaths.instanceEndpointFile,
            waitMillis: Long = 3_000
        ): SingleInstanceGuard {
            require(waitMillis >= 0) { "waitMillis must not be negative" }
            val parent = lockFile.toAbsolutePath().parent
            Files.createDirectories(parent)
            val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis)

            while (true) {
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }

                if (lock != null) {
                    return try {
                        createPrimary(channel, lock, endpointFile)
                    } catch (failure: Throwable) {
                        runCatching { lock.release() }
                        runCatching { channel.close() }
                        throw failure
                    }
                }

                val endpoint = readEndpoint(endpointFile)
                if (endpoint != null && sendActivation(endpoint)) {
                    channel.close()
                    return SingleInstanceGuard(null, null, null, null, null, isPrimary = false)
                }

                if (System.nanoTime() >= deadline) {
                    channel.close()
                    error("Another BillMinder process holds the lock but could not be activated")
                }
                Thread.sleep(RETRY_MILLIS)
            }
        }

        private fun createPrimary(
            channel: FileChannel,
            lock: FileLock,
            endpointFile: Path
        ): SingleInstanceGuard {
            val server = ServerSocket().apply {
                bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1)
            }
            val token = UUID.randomUUID().toString()
            val parent = endpointFile.toAbsolutePath().parent
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, "billminder4pc-endpoint-", ".tmp")
            try {
                Files.writeString(
                    temporary,
                    "${server.localPort}\n$token\n",
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                try {
                    Files.move(
                        temporary,
                        endpointFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, endpointFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (failure: Throwable) {
                runCatching { server.close() }
                throw failure
            } finally {
                Files.deleteIfExists(temporary)
            }
            return SingleInstanceGuard(channel, lock, server, endpointFile, token, isPrimary = true)
        }

        private fun readEndpoint(path: Path): Endpoint? = runCatching {
            val lines = Files.readAllLines(path)
            val port = lines.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
            val token = lines.getOrNull(1)?.let { UUID.fromString(it).toString() } ?: return null
            Endpoint(port, token)
        }.getOrNull()

        private fun sendActivation(endpoint: Endpoint): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.port),
                    SOCKET_TIMEOUT_MILLIS
                )
                socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                DataOutputStream(socket.getOutputStream()).apply {
                    writeUTF(endpoint.token)
                    flush()
                }
                DataInputStream(socket.getInputStream()).readBoolean()
            }
        }.getOrDefault(false)

        private data class Endpoint(val port: Int, val token: String)
    }
}
