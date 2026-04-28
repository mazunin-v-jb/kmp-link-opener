package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager
import dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformFactoryTest {

    @Test
    fun detectsMacOsByMacToken() {
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("Mac OS X"))
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("macOS"))
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("MAC OS X"))
    }

    @Test
    fun detectsMacOsByDarwinToken() {
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("Darwin"))
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("DARWIN 24.0"))
    }

    @Test
    fun detectsWindowsByWinToken() {
        assertEquals(HostOs.Windows, PlatformFactory.detectHostOs("Windows 11"))
        assertEquals(HostOs.Windows, PlatformFactory.detectHostOs("Windows Server 2022"))
        assertEquals(HostOs.Windows, PlatformFactory.detectHostOs("WINDOWS"))
    }

    @Test
    fun detectsLinuxByNuxToken() {
        assertEquals(HostOs.Linux, PlatformFactory.detectHostOs("Linux"))
        assertEquals(HostOs.Linux, PlatformFactory.detectHostOs("Ubuntu Linux"))
        assertEquals(HostOs.Linux, PlatformFactory.detectHostOs("LINUX 6.5"))
    }

    @Test
    fun detectsUnixLikeByNixToken() {
        // "nix" in "unix" — covers SunOS / Solaris descendants reporting as Unix.
        assertEquals(HostOs.Linux, PlatformFactory.detectHostOs("Unix"))
    }

    @Test
    fun detectsAixViaItsToken() {
        assertEquals(HostOs.Linux, PlatformFactory.detectHostOs("AIX"))
    }

    @Test
    fun fallsBackToOtherForUnknownOs() {
        assertEquals(HostOs.Other, PlatformFactory.detectHostOs(""))
        assertEquals(HostOs.Other, PlatformFactory.detectHostOs("Plan 9"))
        assertEquals(HostOs.Other, PlatformFactory.detectHostOs("OS/2"))
        assertEquals(HostOs.Other, PlatformFactory.detectHostOs("FreeBSD"))
        assertEquals(HostOs.Other, PlatformFactory.detectHostOs("HP-UX"))
    }

    @Test
    fun macTakesPrecedenceOverOtherTokens() {
        // If "mac" appears anywhere in the string, classify as MacOs (defensive — unlikely
        // to actually happen, but documents the priority of branches in detectHostOs).
        assertEquals(HostOs.MacOs, PlatformFactory.detectHostOs("Mac Linux Hybrid"))
    }

    // --- factory smoke tests (macOS only — currentOs is lazy and reflects the host) ---

    private val onMacOs: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().let {
            "mac" in it || "darwin" in it
        }

    @Test
    fun createBrowserDiscoveryReturnsMacOsImplOnMacHost() {
        assumeTrue("requires macOS host", onMacOs)
        assertTrue(PlatformFactory.createBrowserDiscovery() is MacOsBrowserDiscovery)
    }

    @Test
    fun createAutoStartManagerReturnsMacOsImplOnMacHost() {
        assumeTrue("requires macOS host", onMacOs)
        assertTrue(PlatformFactory.createAutoStartManager() is MacOsAutoStartManager)
    }

    @Test
    fun createUrlReceiverReturnsJvmImpl() {
        assertNotNull(PlatformFactory.createUrlReceiver())
    }

    @Test
    fun currentOsReflectsHost() {
        val expected = PlatformFactory.detectHostOs(System.getProperty("os.name").orEmpty())
        assertEquals(expected, PlatformFactory.currentOs)
    }
}
