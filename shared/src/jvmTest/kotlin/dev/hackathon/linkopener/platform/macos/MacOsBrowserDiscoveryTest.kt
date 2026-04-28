package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.core.model.BrowserProfile
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacOsBrowserDiscoveryTest {

    @Test
    fun queriesEveryConfiguredSearchRoot() = runTest {
        val root1 = Path("/fake-root-a")
        val root2 = Path("/fake-root-b")
        val seen = mutableListOf<Path>()
        val discovery = newDiscovery(
            roots = listOf(root1, root2),
            scanner = scannerThat {
                seen.add(it)
                emptyList()
            },
        )

        discovery.discover()

        assertEquals(listOf(root1, root2), seen)
    }

    @Test
    fun callsReaderForEveryCandidatePathAndAggregates() = runTest {
        val safari = Path("/fake/Safari.app")
        val chrome = Path("/fake/Chrome.app")
        val readPaths = mutableListOf<Path>()
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(safari, chrome),
            reader = readerThat {
                readPaths.add(it)
                browser("id-${it.fileName}", "Name-${it.fileName}", it)
            },
        )

        val result = discovery.discover()

        assertEquals(setOf(safari, chrome), readPaths.toSet())
        assertEquals(2, result.size)
    }

    @Test
    fun filtersOutNullsFromReader() = runTest {
        val isBrowser = Path("/fake/Browser.app")
        val notBrowser = Path("/fake/SomethingElse.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(isBrowser, notBrowser),
            reader = readerThat { path ->
                if (path == isBrowser) browser("com.example.browser", "Browser", path) else null
            },
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
        assertEquals("com.example.browser", result[0].bundleId)
    }

    @Test
    fun dedupesPathsAcrossRootsBeforeReaderIsCalled() = runTest {
        // The same .app surfaces from two different roots — happens with /System/Applications
        // symlinks aliasing into Cryptex. After resolveSafely + distinct(), the reader
        // should only be invoked once for that canonical path.
        val sharedPath = Path("/fake/shared/Safari.app")
        val readCalls = mutableListOf<Path>()
        val discovery = newDiscovery(
            roots = listOf(Path("/fake/root-1"), Path("/fake/root-2")),
            scanner = scannerThat { listOf(sharedPath) },
            reader = readerThat {
                readCalls.add(it)
                browser("com.apple.Safari", "Safari", it)
            },
        )

        discovery.discover()

        assertEquals(1, readCalls.size)
    }

    @Test
    fun dedupesByApplicationPathInTheFinalList() = runTest {
        // If reader returns two Browser values pointing at the same path (it shouldn't, but
        // if upstream changes ever made that possible we'd want the safety net).
        val path = Path("/fake/Browser.app")
        var first = true
        val discovery = newDiscovery(
            roots = listOf(Path("/r1"), Path("/r2")),
            scanner = scannerThat { root ->
                // same physical path returned from two different scanner calls; resolveSafely
                // collapses these via distinct() since they're equal Path values
                listOf(path)
            },
            reader = readerThat {
                val n = if (first) "First" else "Second"
                first = false
                browser("com.example.browser", n, it)
            },
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
    }

    @Test
    fun sortsByDisplayNameCaseInsensitively() = runTest {
        val a = Path("/fake/A.app")
        val b = Path("/fake/B.app")
        val c = Path("/fake/C.app")
        val byPath = mapOf(
            a to browser("a", "zebra", a),
            b to browser("b", "Apple", b),
            c to browser("c", "monkey", c),
        )
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(a, b, c),
            reader = readerThat { byPath[it] },
        )

        val result = discovery.discover().map { it.displayName }

        assertEquals(listOf("Apple", "monkey", "zebra"), result)
    }

    @Test
    fun returnsEmptyWhenNoCandidates() = runTest {
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = scannerThat { emptyList() },
        )

        assertEquals(emptyList(), discovery.discover())
    }

    @Test
    fun returnsEmptyWhenAllCandidatesAreNonBrowsers() = runTest {
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(Path("/fake/X.app"), Path("/fake/Y.app")),
            reader = readerThat { null },
        )

        assertEquals(emptyList(), discovery.discover())
    }

    @Test
    fun continuesIfReaderReturnsNullForSomeCandidates() = runTest {
        val a = Path("/fake/A.app")
        val b = Path("/fake/B.app")
        val c = Path("/fake/C.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(a, b, c),
            reader = readerThat { path ->
                if (path == b) null else browser("id-$path", "Name-$path", path)
            },
        )

        val result = discovery.discover()

        assertEquals(2, result.size)
        assertTrue(result.none { it.applicationPath.endsWith("B.app") })
    }

    // ---------- Stage 046: profile expansion ----------

    @Test
    fun chromiumWithMultipleProfilesIsExpandedIntoOnePerProfile() = runTest {
        val chromePath = Path("/fake/Google Chrome.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(chromePath),
            reader = readerThat { browser("com.google.Chrome", "Google Chrome", it) },
            profileScanner = scannerThatReturns(
                BrowserProfile("Default", "Personal"),
                BrowserProfile("Profile 1", "Work"),
            ),
        )

        val result = discovery.discover()

        assertEquals(2, result.size)
        // Both rows reference the same parent .app, but different profiles.
        assertTrue(result.all { it.applicationPath == chromePath.toString() })
        assertTrue(result.all { it.family == BrowserFamily.Chromium })
        assertEquals(setOf("Default", "Profile 1"), result.map { it.profile?.id }.toSet())
        assertEquals(setOf("Personal", "Work"), result.map { it.profile?.displayName }.toSet())
    }

    @Test
    fun chromiumWithSingleProfileEmitsOneRecordWithoutProfile() = runTest {
        // Per stage-046 plan Q2: don't dilute the list with a synthetic
        // "Default" entry when only one profile exists.
        val chromePath = Path("/fake/Google Chrome.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(chromePath),
            reader = readerThat { browser("com.google.Chrome", "Google Chrome", it) },
            profileScanner = scannerThatReturns(BrowserProfile("Default", "Vlad")),
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
        assertNull(result[0].profile)
        assertEquals(BrowserFamily.Chromium, result[0].family)
    }

    @Test
    fun chromiumWithNoProfilesEmitsOneRecordWithoutProfile() = runTest {
        // Browser was never opened — Local State doesn't exist.
        val chromePath = Path("/fake/Google Chrome.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(chromePath),
            reader = readerThat { browser("com.google.Chrome", "Google Chrome", it) },
            profileScanner = scannerThatReturns(),
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
        assertNull(result[0].profile)
        assertEquals(BrowserFamily.Chromium, result[0].family)
    }

    @Test
    fun nonChromiumBrowsersAreNotExpandedAndGetOtherFamily() = runTest {
        // Safari (or any unknown) — no profile detection attempted.
        val safariPath = Path("/fake/Safari.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(safariPath),
            reader = readerThat { browser("com.apple.Safari", "Safari", it) },
            profileScanner = scannerThatThrows(), // must not be called
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
        assertNull(result[0].profile)
        assertEquals(BrowserFamily.Safari, result[0].family)
    }

    @Test
    fun unknownBundleIdGetsOtherFamilyAndNoProfileExpansion() = runTest {
        val customPath = Path("/fake/Custom.app")
        val discovery = newDiscovery(
            roots = listOf(Path("/fake")),
            scanner = constantScanner(customPath),
            reader = readerThat { browser("com.example.unknown", "Custom", it) },
            profileScanner = scannerThatThrows(),
        )

        val result = discovery.discover()

        assertEquals(1, result.size)
        assertNull(result[0].profile)
        assertEquals(BrowserFamily.Other, result[0].family)
    }

    // --- helpers ---

    private fun newDiscovery(
        roots: List<Path>,
        scanner: AppBundleScanner = scannerThat { emptyList() },
        reader: InfoPlistReader = readerThat { null },
        profileScanner: ChromiumProfileScanner = scannerThatReturns(),
    ): MacOsBrowserDiscovery = MacOsBrowserDiscovery(
        scanner = scanner,
        plistReader = reader,
        searchRoots = roots,
        profileScanner = profileScanner,
        // Tests never read real `~/Library/Application Support/` — feed a
        // fake root that the fake profileScanner ignores anyway.
        applicationSupportDir = Path("/tmp/fake-app-support-${System.nanoTime()}"),
    )

    private fun scannerThatReturns(vararg profiles: BrowserProfile): ChromiumProfileScanner =
        object : ChromiumProfileScanner() {
            override fun scan(userDataDir: Path): List<BrowserProfile> = profiles.toList()
        }

    private fun scannerThatThrows(): ChromiumProfileScanner =
        object : ChromiumProfileScanner() {
            override fun scan(userDataDir: Path): List<BrowserProfile> =
                error("profile scanner should not be called for non-Chromium browser")
        }

    private fun scannerThat(block: (Path) -> List<Path>): AppBundleScanner =
        object : AppBundleScanner() {
            override fun findAppBundles(root: Path, maxDepth: Int): List<Path> = block(root)
        }

    private fun constantScanner(vararg paths: Path): AppBundleScanner =
        scannerThat { paths.toList() }

    private fun readerThat(block: (Path) -> Browser?): InfoPlistReader =
        object : InfoPlistReader() {
            override fun readBrowser(appBundlePath: Path): Browser? = block(appBundlePath)
        }

    private fun browser(id: String, name: String, path: Path): Browser =
        Browser(
            bundleId = id,
            displayName = name,
            applicationPath = path.toString(),
            version = "1.0",
        )
}
