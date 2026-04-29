package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.BrowserProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromiumProfileScannerTest {

    private val tempDirs = mutableListOf<Path>()
    private val scanner = ChromiumProfileScanner()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { dir ->
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
        tempDirs.clear()
    }

    @Test
    fun returnsEmptyWhenLocalStateMissing() {
        // Browser was never opened — user-data dir might not even exist; if it
        // does, `Local State` won't.
        val dir = newDir()

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun returnsEmptyOnMalformedJson() {
        val dir = newDir().withLocalState("this is not json")

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun returnsEmptyWhenInfoCacheMissing() {
        val dir = newDir().withLocalState(
            """{"profile":{"last_used":"Default"}}""",
        )

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun parsesSingleDefaultProfile() {
        val dir = newDir().withLocalState(
            """
            {
              "profile": {
                "info_cache": {
                  "Default": { "name": "Vlad" }
                }
              }
            }
            """.trimIndent(),
        )

        val profiles = scanner.scan(dir)

        assertEquals(listOf(BrowserProfile(id = "Default", displayName = "Vlad")), profiles)
    }

    @Test
    fun parsesMultipleProfilesAndPutsDefaultFirst() {
        val dir = newDir().withLocalState(
            """
            {
              "profile": {
                "info_cache": {
                  "Profile 2": { "name": "Hobby" },
                  "Default":   { "name": "Personal" },
                  "Profile 1": { "name": "Work" }
                }
              }
            }
            """.trimIndent(),
        )

        val profiles = scanner.scan(dir)

        assertEquals(
            listOf(
                BrowserProfile("Default", "Personal"),
                BrowserProfile("Profile 1", "Work"),
                BrowserProfile("Profile 2", "Hobby"),
            ),
            profiles,
        )
    }

    @Test
    fun fallsBackToGaiaNameWhenNameMissing() {
        val dir = newDir().withLocalState(
            """
            {
              "profile": {
                "info_cache": {
                  "Default": { "gaia_name": "vlad@example.com" }
                }
              }
            }
            """.trimIndent(),
        )

        val profiles = scanner.scan(dir)

        assertEquals(listOf(BrowserProfile("Default", "vlad@example.com")), profiles)
    }

    @Test
    fun fallsBackToIdWhenAllNamesMissing() {
        val dir = newDir().withLocalState(
            """
            {
              "profile": {
                "info_cache": {
                  "Default":   {},
                  "Profile 1": {}
                }
              }
            }
            """.trimIndent(),
        )

        val profiles = scanner.scan(dir)

        assertEquals(
            listOf(
                BrowserProfile("Default", "Default"),
                BrowserProfile("Profile 1", "Profile 1"),
            ),
            profiles,
        )
    }

    @Test
    fun returnsEmptyWhenProfileFieldIsMissing() {
        // Local State without a "profile" key — uncommon but possible on
        // freshly-installed Chrome before any profile has been opened.
        val dir = newDir().withLocalState("""{}""")

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun returnsEmptyWhenProfileFieldIsNotAnObject() {
        // Defensive: corrupted Local State where "profile" was a string
        // (some old Chromium fork or a sync glitch). Type-cast guard kicks in.
        val dir = newDir().withLocalState("""{"profile":"oops"}""")

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun returnsEmptyWhenInfoCacheIsNotAnObject() {
        // info_cache typed wrong — e.g. an array instead of a map.
        val dir = newDir().withLocalState(
            """{"profile":{"info_cache":[]}}""",
        )

        val profiles = scanner.scan(dir)

        assertEquals(emptyList(), profiles)
    }

    @Test
    fun nonObjectEntryFallsBackToIdAsDisplayName() {
        // info_cache contains a non-JsonObject entry — the scanner should
        // still emit the profile, falling back to id-as-name (the
        // `obj as? JsonObject` cast goes null and Elvis chain reaches `id`).
        val dir = newDir().withLocalState(
            """{"profile":{"info_cache":{"Default":"oddly a string","Profile 1":{"name":"Work"}}}}""",
        )

        val profiles = scanner.scan(dir)

        assertEquals(
            listOf(
                BrowserProfile("Default", "Default"),
                BrowserProfile("Profile 1", "Work"),
            ),
            profiles,
        )
    }

    @Test
    fun blankNameFallsBackToGaiaName() {
        // The string() helper rejects blank strings via takeIf { isNotBlank() }
        // so a "name":"" should fall through to gaia_name. Cookie-domain
        // shortcut for "name was set to nothing".
        val dir = newDir().withLocalState(
            """{"profile":{"info_cache":{"Default":{"name":"","gaia_name":"v@x.com"}}}}""",
        )

        val profiles = scanner.scan(dir)

        assertEquals(listOf(BrowserProfile("Default", "v@x.com")), profiles)
    }

    @Test
    fun blankNameAndBlankGaiaNameFallBackToId() {
        val dir = newDir().withLocalState(
            """{"profile":{"info_cache":{"Profile 7":{"name":"","gaia_name":""}}}}""",
        )

        val profiles = scanner.scan(dir)

        assertEquals(listOf(BrowserProfile("Profile 7", "Profile 7")), profiles)
    }

    @Test
    fun unicodeAndEmojiNamesAreHandled() {
        val dir = newDir().withLocalState(
            """
            {
              "profile": {
                "info_cache": {
                  "Default": { "name": "Личный 🦊" }
                }
              }
            }
            """.trimIndent(),
        )

        val profiles = scanner.scan(dir)

        assertEquals(listOf(BrowserProfile("Default", "Личный 🦊")), profiles)
    }

    // --- helpers ---

    private fun newDir(): Path =
        Files.createTempDirectory("chromium-profile-scanner-test").also { tempDirs.add(it) }

    private fun Path.withLocalState(content: String): Path = apply {
        resolve("Local State").writeText(content)
    }
}
