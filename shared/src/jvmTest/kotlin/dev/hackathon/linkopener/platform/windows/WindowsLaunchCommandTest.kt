package dev.hackathon.linkopener.platform.windows

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsLaunchCommandTest {

    @Test
    fun quoteWrapsExeInDoubleQuotes() {
        val tokens = listOf("C:\\Program Files\\Link Opener\\Link Opener.exe")
        assertEquals(
            "\"C:\\Program Files\\Link Opener\\Link Opener.exe\"",
            WindowsLaunchCommand.quote(tokens),
        )
    }

    @Test
    fun quoteJavaJarShapeKeepsBareJarFlag() {
        val tokens = listOf(
            "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
            "-jar",
            "C:\\Users\\u\\link-opener.jar",
        )
        assertEquals(
            "\"C:\\Program Files\\Java\\jdk-21\\bin\\java.exe\" -jar " +
                "\"C:\\Users\\u\\link-opener.jar\"",
            WindowsLaunchCommand.quote(tokens),
        )
    }

    @Test
    fun quoteEmptyListReturnsEmptyString() {
        assertEquals("", WindowsLaunchCommand.quote(emptyList()))
    }

    @Test
    fun quotePreservesSpacesInPathsViaQuotes() {
        // `C:\Program Files\…` is the canonical reason we quote at all —
        // the OS launcher would split on spaces otherwise.
        val tokens = listOf("C:\\Program Files (x86)\\Foo\\bar.exe")
        val quoted = WindowsLaunchCommand.quote(tokens)
        // The whole path stays inside one quoted token.
        assertEquals(1, quoted.count { it == '"' } / 2)
        assertEquals("\"C:\\Program Files (x86)\\Foo\\bar.exe\"", quoted)
    }

    // Note: `WindowsLaunchCommand.current()` is exercised on real
    // ProcessHandle data and isn't directly testable without spawning
    // a subprocess. The detection logic is small enough that the
    // quote() coverage above plus the integration in WindowsHandlerRegistration
    // / WindowsAutoStartManager tests cover the relevant paths.
}
