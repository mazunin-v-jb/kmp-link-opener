package dev.hackathon.linkopener.platform.windows

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsHandlerRegistrationTest {

    @Test
    fun registerWritesAllNineRegistryEntriesAndReturnsTrue() = runTest {
        val recorder = RecordingRunner()
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\Program Files\\Link Opener\\Link Opener.exe" },
        )

        val ok = reg.register()

        assertTrue(ok)
        // Nine `reg add` calls — see WindowsHandlerRegistration for the list:
        // ProgId default, ProgId shell\open\command,
        // Capabilities ApplicationName, ApplicationDescription, http, https,
        // StartMenuInternet (Default), StartMenuInternet shell\open\command,
        // RegisteredApplications LinkOpener.
        assertEquals(9, recorder.calls.count { it[1] == "add" })
    }

    @Test
    fun registerWritesProgIdShellOpenCommandWithQuotedExeAndPercent1() = runTest {
        val recorder = RecordingRunner()
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\App\\LinkOpener.exe" },
        )

        reg.register()

        // The crucial line: shell\open\command must invoke us as
        // `"<exe>" "%1"` so Windows substitutes %1 with the URL when
        // launching us as the default browser.
        val shellOpen = recorder.calls.firstOrNull {
            it[1] == "add" &&
                it.contains("HKCU\\SOFTWARE\\Classes\\LinkOpener.URL\\shell\\open\\command") &&
                it.contains("(Default)")
        }
        assertEquals("\"C:\\App\\LinkOpener.exe\" \"%1\"", shellOpen?.let { it[it.indexOf("/d") + 1] })
    }

    @Test
    fun registerWritesUrlAssociationsForBothHttpAndHttps() = runTest {
        val recorder = RecordingRunner()
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\X.exe" },
        )

        reg.register()

        val urlAssocs = recorder.calls.filter {
            it[1] == "add" &&
                it.any { arg -> arg.endsWith("URLAssociations") }
        }
        assertEquals(2, urlAssocs.size)
        // Both point at our ProgId.
        urlAssocs.forEach { call ->
            assertEquals("LinkOpener.URL", call[call.indexOf("/d") + 1])
        }
    }

    @Test
    fun registerWritesRegisteredApplicationsAtomLast() = runTest {
        val recorder = RecordingRunner()
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\X.exe" },
        )

        reg.register()

        // The RegisteredApplications atom must point to the
        // Capabilities sub-tree — Windows joins by string equality.
        val regApps = recorder.calls.firstOrNull {
            it[1] == "add" &&
                it.contains("HKCU\\SOFTWARE\\RegisteredApplications") &&
                it.contains("LinkOpener")
        }
        assertEquals(
            "Software\\Clients\\StartMenuInternet\\LinkOpener\\Capabilities",
            regApps?.let { it[it.indexOf("/d") + 1] },
        )
    }

    @Test
    fun registerReturnsFalseWhenExePathUnknown() = runTest {
        val recorder = RecordingRunner()
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { null }, // ProcessHandle didn't yield a path
        )

        assertFalse(reg.register())
        assertEquals(emptyList(), recorder.calls)
    }

    @Test
    fun registerReturnsFalseOnAnyRegistryWriteFailure() = runTest {
        // Recorder returns null (= failure) for the third call — the
        // registration boolean accumulator should fold that to false.
        val recorder = RecordingRunner(failAfter = 3)
        val reg = WindowsHandlerRegistration(
            registry = RegistryReader(runner = recorder),
            exePathProvider = { "C:\\X.exe" },
        )

        assertFalse(reg.register())
    }

    /**
     * Records every argv passed to the runner. By default returns "" for
     * every `reg add` (= success); when [failAfter] is set, returns null
     * (= failure) for the (failAfter+1)-th call onwards.
     */
    private class RecordingRunner(
        private val failAfter: Int = Int.MAX_VALUE,
    ) : (List<String>) -> String? {
        val calls = mutableListOf<List<String>>()

        override fun invoke(args: List<String>): String? {
            calls += args
            return if (calls.size > failAfter) null else ""
        }
    }
}
