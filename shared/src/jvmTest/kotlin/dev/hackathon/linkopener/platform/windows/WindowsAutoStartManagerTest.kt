package dev.hackathon.linkopener.platform.windows

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsAutoStartManagerTest {

    @Test
    fun isEnabledTrueWhenRunValueExists() = runTest {
        val recorder = RecordingRunner(
            scripted = mapOf(
                listOf(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", "LinkOpener",
                ) to """
                    HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Run
                        LinkOpener    REG_SZ    "C:\Program Files\Link Opener\Link Opener.exe"

                """.trimIndent(),
            ),
        )
        val manager = WindowsAutoStartManager(RegistryReader(runner = recorder))

        assertTrue(manager.isEnabled())
    }

    @Test
    fun isEnabledFalseWhenRunValueAbsent() = runTest {
        val recorder = RecordingRunner() // empty scripts → query returns null
        val manager = WindowsAutoStartManager(RegistryReader(runner = recorder))

        assertFalse(manager.isEnabled())
    }

    @Test
    fun setEnabledTrueAddsValueWithQuotedExePath() = runTest {
        val recorder = RecordingRunner()
        val manager = WindowsAutoStartManager(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\Program Files\\Link Opener\\Link Opener.exe" },
        )

        manager.setEnabled(true)

        assertEquals(
            listOf(
                "reg", "add",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", "LinkOpener",
                "/t", "REG_SZ",
                "/d", "\"C:\\Program Files\\Link Opener\\Link Opener.exe\"",
                "/f",
            ),
            recorder.calls.last(),
        )
    }

    @Test
    fun setEnabledFalseDeletesValue() = runTest {
        val recorder = RecordingRunner()
        val manager = WindowsAutoStartManager(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\unused.exe" },
        )

        manager.setEnabled(false)

        assertEquals(
            listOf(
                "reg", "delete",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", "LinkOpener",
                "/f",
            ),
            recorder.calls.last(),
        )
    }

    @Test
    fun setEnabledIsIdempotent() = runTest {
        // Calling enable twice is safe — `reg add` overwrites with /f.
        val recorder = RecordingRunner()
        val manager = WindowsAutoStartManager(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\app.exe" },
        )

        manager.setEnabled(true)
        manager.setEnabled(true)

        assertEquals(2, recorder.calls.size)
        assertEquals("reg", recorder.calls[0][0])
        assertEquals("add", recorder.calls[0][1])
        assertEquals("reg", recorder.calls[1][0])
        assertEquals("add", recorder.calls[1][1])
    }

    @Test
    fun setEnabledTrueIsNoOpWhenExePathUnknown() = runTest {
        val recorder = RecordingRunner()
        val manager = WindowsAutoStartManager(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { null }, // ProcessHandle didn't return a path
        )

        manager.setEnabled(true)

        // No registry write attempted.
        assertEquals(emptyList(), recorder.calls)
    }

    /**
     * Records every argv passed to the runner and returns scripted
     * output (or null) per the [scripted] map. Used to assert that
     * autostart toggles produce the right `reg add` / `reg delete`
     * argv sequences.
     */
    private class RecordingRunner(
        private val scripted: Map<List<String>, String> = emptyMap(),
    ) : (List<String>) -> String? {
        val calls = mutableListOf<List<String>>()

        override fun invoke(args: List<String>): String? {
            calls += args
            // For mutating calls (`reg add`, `reg delete`) we don't
            // care what the output looks like — non-null means "command
            // succeeded" to the caller. Empty string for those; the
            // scripted map handles read paths.
            return scripted[args] ?: if (args.getOrNull(1) in setOf("add", "delete")) "" else null
        }
    }
}
