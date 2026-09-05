package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The stored preference has to describe the task that actually exists. A checkbox that stays on
 * after the registration failed tells the user reminders will arrive when they will not.
 */
class StartAtLoginSettingTest {

    private class FakeStartupTasks(
        var registered: Boolean? = false,
        var unavailable: StartupRegistration.Unavailable? = null,
        var failWith: String? = null
    ) : StartupTasks {
        var registerCalls = 0
        var unregisterCalls = 0

        override fun unavailableReason() = unavailable
        override fun isRegistered() = registered

        override fun register(): Result<Unit> {
            registerCalls++
            failWith?.let { return Result.failure(IllegalStateException(it)) }
            registered = true
            return Result.success(Unit)
        }

        override fun unregister(): Result<Unit> {
            unregisterCalls++
            failWith?.let { return Result.failure(IllegalStateException(it)) }
            registered = false
            return Result.success(Unit)
        }
    }

    private fun state(tasks: StartupTasks, store: AppPreferencesStore = AppPreferencesStore()) =
        AppState(
            db = DatabaseFactory.openInMemory(),
            zone = ZoneId.of("UTC"),
            dayChangeSignals = emptyFlow(),
            reminderTickSignals = emptyFlow(),
            preferencesStore = store,
            startupRegistration = tasks
        )

    @Test
    fun `turning it on registers the task and stores the choice`() = runBlocking {
        val tasks = FakeStartupTasks()
        val state = state(tasks)
        try {
            state.setStartAtLogin(true)
            assertEquals(1, tasks.registerCalls)
            assertEquals(true, tasks.registered)
            assertTrue(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `turning it off removes the task`() = runBlocking {
        val tasks = FakeStartupTasks(registered = true)
        val state = state(tasks)
        try {
            state.setStartAtLogin(false)
            assertEquals(1, tasks.unregisterCalls)
            assertEquals(false, tasks.registered)
            assertFalse(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `a failed registration leaves the setting off and says why`() = runBlocking {
        val tasks = FakeStartupTasks(failWith = "Access is denied.")
        val state = state(tasks)
        try {
            state.setStartAtLogin(true)
            assertFalse(state.preferences.value.startAtLogin)
            assertEquals("Access is denied.", state.errorMessage.value)
        } finally {
            state.close()
        }
    }

    @Test
    fun `a failed removal stores the task that is really still there`() = runBlocking {
        // Starts stored-off but actually registered, so the corrective write is the only thing
        // that can produce a true. Without it the checkbox would claim the task is gone.
        val tasks = FakeStartupTasks(registered = true, failWith = "The service is unavailable.")
        val store = AppPreferencesStore()
        val state = state(tasks, store)
        try {
            assertTrue(state.preferences.value.startAtLogin)
            store.update { it.copy(startAtLogin = false) }

            state.setStartAtLogin(false)

            assertEquals("The service is unavailable.", state.errorMessage.value)
            assertTrue(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `an unanswerable scheduler leaves the stored setting alone`() = runBlocking {
        val store = AppPreferencesStore()
        store.update { it.copy(startAtLogin = true) }
        // null means the query failed, not that the task is absent. Treating it as absent would
        // switch the user's setting off because a service was briefly unreachable.
        val state = state(FakeStartupTasks(registered = null), store)
        try {
            assertTrue(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `a task removed outside the app corrects the stored preference at startup`() = runBlocking {
        val store = AppPreferencesStore()
        store.update { it.copy(startAtLogin = true) }
        val state = state(FakeStartupTasks(registered = false), store)
        try {
            assertFalse(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `a task added outside the app is reflected at startup`() = runBlocking {
        val store = AppPreferencesStore()
        val state = state(FakeStartupTasks(registered = true), store)
        try {
            assertTrue(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }

    @Test
    fun `an unsupported platform explains itself and never touches the scheduler`() = runBlocking {
        val tasks = FakeStartupTasks(unavailable = StartupRegistration.Unavailable.NotInstalled)
        val store = AppPreferencesStore()
        store.update { it.copy(startAtLogin = true) }
        val state = state(tasks, store)
        try {
            assertNotNull(state.startupUnavailableMessage)
            // Reconciliation must not run, or a dev build would silently clear a real setting.
            assertTrue(state.preferences.value.startAtLogin)
        } finally {
            state.close()
        }
    }
}
