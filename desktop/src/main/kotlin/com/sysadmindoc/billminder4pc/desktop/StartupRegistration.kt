package com.sysadmindoc.billminder4pc.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The sign-in registration seen from the app's side. Exists so tests can exercise the settings
 * behaviour without registering real scheduled tasks on the developer's machine.
 */
interface StartupTasks {
    fun unavailableReason(): StartupRegistration.Unavailable?

    /** True registered, false absent, null when Task Scheduler could not be asked. */
    fun isRegistered(): Boolean?
    fun register(): Result<Unit>
    fun unregister(): Result<Unit>
}

/**
 * Registers the app to start at logon through Task Scheduler.
 *
 * Task Scheduler rather than a Run key because the Run key cannot express the power conditions
 * that matter here: this is a Modern Standby machine, and a task left with the default battery
 * conditions is silently held back with no missed-run catch-up.
 */
object StartupRegistration : StartupTasks {

    const val TASK_NAME = "BillMinder for PC"

    private const val DESCRIPTION =
        "Starts BillMinder for PC at sign-in so bill reminders arrive " +
            "whether or not the window was opened."

    /** Why a registration could not be attempted, in words a settings page can show. */
    sealed interface Unavailable {
        val message: String

        data object NotWindows : Unavailable {
            override val message: String = "Starting at sign-in is a Windows feature."
        }

        data object NotInstalled : Unavailable {
            override val message: String =
                "Starting at sign-in needs the installed app. This copy is running from a " +
                    "development build."
        }
    }

    val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /**
     * The executable Task Scheduler should launch, or null when this process is a bare JVM.
     * A development run would otherwise register `java.exe` and start nothing useful at logon.
     */
    fun currentExecutable(): Path? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        val path = runCatching { Path.of(command) }.getOrNull() ?: return null
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (name == "java.exe" || name == "javaw.exe" || name == "java") return null
        return path
    }

    /** The reason registration cannot proceed, or null when it can. */
    override fun unavailableReason(): Unavailable? = unavailableReason(currentExecutable())

    fun unavailableReason(executable: Path?): Unavailable? = when {
        !isWindows -> Unavailable.NotWindows
        executable == null -> Unavailable.NotInstalled
        else -> null
    }

    /**
     * The Task Scheduler definition. Battery conditions are off deliberately: the default
     * `DisallowStartIfOnBatteries` is what makes a task on this kind of machine never run.
     *
     * `WakeToRun` is inert for a logon trigger, which fires on a session the user is already
     * present for. It is set so the definition stays correct if a time trigger is ever added.
     */
    fun taskXml(executable: String, userId: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-16"?>""")
        appendLine(
            """<Task version="1.4" """ +
                """xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">"""
        )
        appendLine("  <RegistrationInfo>")
        appendLine("    <Description>${escape(DESCRIPTION)}</Description>")
        appendLine("  </RegistrationInfo>")
        appendLine("  <Triggers>")
        appendLine("    <LogonTrigger>")
        appendLine("      <Enabled>true</Enabled>")
        appendLine("      <UserId>${escape(userId)}</UserId>")
        appendLine("    </LogonTrigger>")
        appendLine("  </Triggers>")
        appendLine("""  <Principals>""")
        appendLine("""    <Principal id="Author">""")
        appendLine("      <UserId>${escape(userId)}</UserId>")
        appendLine("      <LogonType>InteractiveToken</LogonType>")
        appendLine("      <RunLevel>LeastPrivilege</RunLevel>")
        appendLine("    </Principal>")
        appendLine("  </Principals>")
        appendLine("  <Settings>")
        appendLine("    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>")
        appendLine("    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>")
        appendLine("    <WakeToRun>true</WakeToRun>")
        appendLine("    <StartWhenAvailable>true</StartWhenAvailable>")
        appendLine("    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>")
        appendLine("    <RunOnlyIfIdle>false</RunOnlyIfIdle>")
        appendLine("    <AllowStartOnDemand>true</AllowStartOnDemand>")
        appendLine("    <Enabled>true</Enabled>")
        appendLine("    <Hidden>false</Hidden>")
        appendLine("    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>")
        appendLine("    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>")
        appendLine("    <IdleSettings>")
        appendLine("      <StopOnIdleEnd>false</StopOnIdleEnd>")
        appendLine("      <RestartOnIdle>false</RestartOnIdle>")
        appendLine("    </IdleSettings>")
        appendLine("  </Settings>")
        appendLine("""  <Actions Context="Author">""")
        appendLine("    <Exec>")
        appendLine("      <Command>${escape(executable)}</Command>")
        appendLine("    </Exec>")
        appendLine("  </Actions>")
        append("</Task>")
    }

    override fun isRegistered(): Boolean? = isRegistered(TASK_NAME)

    /**
     * True when the task exists, false when it does not, null when the question could not be
     * answered.
     *
     * `schtasks /query /tn` exits non-zero both for "no such task" and for "the service is
     * unavailable", and telling them apart by the message would mean parsing localised English.
     * A second query with no task name settles it: if Task Scheduler can list anything at all it
     * is reachable, so the first failure really did mean absent. Without this an unreachable
     * scheduler reads as absent, and the caller helpfully turns the user's setting off.
     */
    fun isRegistered(taskName: String): Boolean? {
        if (!isWindows) return false
        if (runSchtasks(listOf("/query", "/tn", taskName)).exitCode == 0) return true
        return if (runSchtasks(listOf("/query")).exitCode == 0) false else null
    }

    /**
     * Writes the definition and hands it to `schtasks`. The XML file has to be UTF-16 with a byte
     * order mark; `schtasks /xml` rejects UTF-8.
     */
    override fun register(): Result<Unit> = register(TASK_NAME)

    fun register(
        taskName: String,
        executable: Path? = currentExecutable(),
        userId: String = currentUserId()
    ): Result<Unit> {
        unavailableReason(executable)?.let { return Result.failure(IllegalStateException(it.message)) }
        val target = requireNotNull(executable)
        val xml = taskXml(target.toAbsolutePath().toString(), userId)
        val file = Files.createTempFile("billminder4pc-task", ".xml")
        return try {
            Files.write(file, xml.toByteArray(StandardCharsets.UTF_16LE).withUtf16Bom())
            val result = runSchtasks(
                listOf("/create", "/tn", taskName, "/xml", file.toAbsolutePath().toString(), "/f")
            )
            if (result.exitCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(result.describeFailure("register")))
            }
        } catch (failure: Throwable) {
            Result.failure(failure)
        } finally {
            runCatching { Files.deleteIfExists(file) }
        }
    }

    /** Removes the task. Succeeds when it was already absent. */
    override fun unregister(): Result<Unit> = unregister(TASK_NAME)

    fun unregister(taskName: String): Result<Unit> {
        if (!isWindows) return Result.success(Unit)
        // Only skip the delete when the task is known to be absent. An unanswerable query must
        // still attempt it, or a scheduler hiccup reports "removed" while the task keeps running.
        if (isRegistered(taskName) == false) return Result.success(Unit)
        val result = runSchtasks(listOf("/delete", "/tn", taskName, "/f"))
        return if (result.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.describeFailure("remove")))
        }
    }

    fun currentUserId(): String {
        val domain = System.getenv("USERDOMAIN")
        val user = System.getProperty("user.name").orEmpty()
        return if (domain.isNullOrBlank()) user else "$domain\\$user"
    }

    private fun ByteArray.withUtf16Bom(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + this

    private data class SchtasksResult(val exitCode: Int, val output: String) {
        fun describeFailure(verb: String): String {
            val detail = output.lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() }
                ?: "schtasks exited with $exitCode"
            return "Couldn't $verb the sign-in task. $detail"
        }
    }

    /**
     * Runs `schtasks` and gives up after [TIMEOUT_SECONDS].
     *
     * The output is drained on its own thread. Reading it inline would block until the child
     * closes stdout, which makes a later `waitFor` timeout unreachable: a hung `schtasks` would
     * hang the caller, and both callers run on a UI thread.
     */
    private fun runSchtasks(arguments: List<String>): SchtasksResult = try {
        val process = ProcessBuilder(listOf("schtasks") + arguments)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val drain = Thread {
            runCatching { process.inputStream.bufferedReader().use { output.append(it.readText()) } }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            drain.join(1_000)
            SchtasksResult(-1, "schtasks did not finish within $TIMEOUT_SECONDS seconds")
        } else {
            drain.join(1_000)
            SchtasksResult(process.exitValue(), output.toString())
        }
    } catch (failure: Throwable) {
        SchtasksResult(-1, failure.message ?: failure.javaClass.simpleName)
    }

    private const val TIMEOUT_SECONDS = 20L

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
