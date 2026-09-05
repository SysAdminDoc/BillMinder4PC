package com.sysadmindoc.billminder4pc.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Path
import java.util.UUID

class StartupRegistrationTest {

    @Test
    fun `the definition turns the power conditions off and keeps the logon trigger`() {
        val xml = StartupRegistration.taskXml("C:\\Program Files\\BillMinder\\app.exe", "PC\\matt")

        assertTrue(xml.contains("<DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>"))
        assertTrue(xml.contains("<StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>"))
        assertTrue(xml.contains("<WakeToRun>true</WakeToRun>"))
        assertTrue(xml.contains("<StartWhenAvailable>true</StartWhenAvailable>"))
        assertTrue(xml.contains("<LogonTrigger>"))
        assertTrue(xml.contains("<LogonType>InteractiveToken</LogonType>"))
        assertTrue(xml.contains("<RunLevel>LeastPrivilege</RunLevel>"))
        assertTrue(xml.contains("<Command>C:\\Program Files\\BillMinder\\app.exe</Command>"))
        assertTrue(xml.contains("<UserId>PC\\matt</UserId>"))
    }

    @Test
    fun `the definition declares the UTF-16 encoding schtasks demands`() {
        val xml = StartupRegistration.taskXml("C:\\app.exe", "PC\\matt")
        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-16"?>"""))
    }

    @Test
    fun `a path with XML-significant characters is escaped, not injected`() {
        val xml = StartupRegistration.taskXml("C:\\a & b\\<app>.exe", "PC\\a\"b")
        assertTrue(xml.contains("<Command>C:\\a &amp; b\\&lt;app&gt;.exe</Command>"))
        assertTrue(xml.contains("""<UserId>PC\a&quot;b</UserId>"""))
        assertFalse(xml.contains("<app>.exe"))
    }

    @Test
    fun `a bare JVM is not a registrable executable`() {
        assertEquals(
            StartupRegistration.Unavailable.NotInstalled,
            StartupRegistration.unavailableReason(executable = null)
        )
    }

    @Test
    fun `the running test JVM reports itself as not installed`() {
        // The suite runs under java.exe, so currentExecutable must refuse it rather than
        // registering a scheduled task that would launch a bare JVM at sign-in.
        assumeTrue(StartupRegistration.isWindows)
        assertEquals(null, StartupRegistration.currentExecutable())
    }

    @Test
    fun `a task can be registered, found, and removed`() {
        assumeTrue(StartupRegistration.isWindows)
        val taskName = "BillMinder4PC test ${UUID.randomUUID()}"
        val executable = Path.of(System.getProperty("java.home"), "bin", "java.exe")
        assertFalse(StartupRegistration.isRegistered(taskName))
        try {
            val created = StartupRegistration.register(
                taskName = taskName,
                executable = executable,
                userId = StartupRegistration.currentUserId()
            )
            assertTrue("register failed: ${created.exceptionOrNull()?.message}", created.isSuccess)
            assertTrue(StartupRegistration.isRegistered(taskName))
        } finally {
            assertTrue(StartupRegistration.unregister(taskName).isSuccess)
        }
        assertFalse(StartupRegistration.isRegistered(taskName))
    }

    @Test
    fun `removing a task that was never there succeeds`() {
        assumeTrue(StartupRegistration.isWindows)
        val absent = "BillMinder4PC absent ${UUID.randomUUID()}"
        assertTrue(StartupRegistration.unregister(absent).isSuccess)
    }
}
