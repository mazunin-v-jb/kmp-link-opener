package dev.hackathon.linkopener.platform.macos

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlistJsonParserTest {

    private val parser = PlistJsonParser()

    @Test
    fun parsesChromeWithDisplayName() {
        val json = """
            {
              "CFBundleIdentifier": "com.google.Chrome",
              "CFBundleName": "Chrome",
              "CFBundleDisplayName": "Google Chrome",
              "CFBundleShortVersionString": "131.0.6778.86",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http", "https"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Google Chrome.app"))

        assertNotNull(browser)
        assertEquals("com.google.Chrome", browser.bundleId)
        assertEquals("Google Chrome", browser.displayName)
        assertEquals("131.0.6778.86", browser.version)
        assertEquals("/Applications/Google Chrome.app", browser.applicationPath)
    }

    @Test
    fun fallsBackToBundleNameWhenDisplayNameMissing() {
        val json = """
            {
              "CFBundleIdentifier": "org.mozilla.firefox",
              "CFBundleName": "Firefox",
              "CFBundleShortVersionString": "120.0",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http", "https"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Firefox.app"))

        assertNotNull(browser)
        assertEquals("Firefox", browser.displayName)
    }

    @Test
    fun fallsBackToAppNameWhenBothNamesMissing() {
        val json = """
            {
              "CFBundleIdentifier": "io.example.weirdbrowser",
              "CFBundleShortVersionString": "1.0",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["https"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Weird Browser.app"))

        assertNotNull(browser)
        assertEquals("Weird Browser", browser.displayName)
    }

    @Test
    fun returnsNullWhenSchemeIsNotHttp() {
        val json = """
            {
              "CFBundleIdentifier": "com.tinyspeck.slackmacgap",
              "CFBundleName": "Slack",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["slack"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Slack.app"))

        assertNull(browser)
    }

    @Test
    fun returnsNullWhenNoUrlTypes() {
        val json = """
            {
              "CFBundleIdentifier": "com.example.notabrowser",
              "CFBundleName": "Calculator"
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Calculator.app"))

        assertNull(browser)
    }

    @Test
    fun returnsNullForCorruptedJson() {
        val browser = parser.parseBrowser("{ this is not valid", Path("/Applications/Whatever.app"))

        assertNull(browser)
    }

    @Test
    fun fallsBackToCFBundleVersionWhenShortVersionMissing() {
        val json = """
            {
              "CFBundleIdentifier": "com.example.browser",
              "CFBundleName": "Example Browser",
              "CFBundleVersion": "42",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Example Browser.app"))

        assertNotNull(browser)
        assertEquals("42", browser.version)
    }

    @Test
    fun versionIsNullWhenAbsent() {
        val json = """
            {
              "CFBundleIdentifier": "com.example.browser",
              "CFBundleName": "Example Browser",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Example Browser.app"))

        assertNotNull(browser)
        assertNull(browser.version)
    }

    @Test
    fun handlesMixedCaseSchemes() {
        val json = """
            {
              "CFBundleIdentifier": "com.example.browser",
              "CFBundleName": "Example Browser",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["HTTP", "HTTPS"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Example Browser.app"))

        assertNotNull(browser)
    }

    @Test
    fun returnsNullWhenBundleIdMissing() {
        val json = """
            {
              "CFBundleName": "No Id Browser",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http"] }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/No Id Browser.app"))

        assertNull(browser)
    }

    @Test
    fun returnsNullWhenUrlTypesIsNotAnArray() {
        // Defensive: if CFBundleURLTypes is a string (malformed plist), treat it as
        // "no URL types" and skip.
        val json = """
            {
              "CFBundleIdentifier": "com.example.malformed",
              "CFBundleName": "Malformed",
              "CFBundleURLTypes": "this should be an array"
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Malformed.app"))

        assertNull(browser)
    }

    @Test
    fun skipsUrlTypeEntriesThatAreNotDicts() {
        // First entry is a stray string (skipped), second is a real dict declaring http.
        val json = """
            {
              "CFBundleIdentifier": "com.example.mixed",
              "CFBundleName": "Mixed",
              "CFBundleURLTypes": [
                "ignore-me",
                {
                  "CFBundleURLSchemes": ["http"]
                }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Mixed.app"))

        assertNotNull(browser)
        assertEquals("com.example.mixed", browser.bundleId)
    }

    @Test
    fun skipsUrlTypeWhenSchemesIsNotAnArray() {
        // CFBundleURLSchemes is a string, not an array — should be skipped, and since
        // no other entry declares http, we return null.
        val json = """
            {
              "CFBundleIdentifier": "com.example.noschemes",
              "CFBundleName": "No Schemes",
              "CFBundleURLTypes": [
                {
                  "CFBundleURLName": "Stub",
                  "CFBundleURLSchemes": "http"
                }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/No Schemes.app"))

        assertNull(browser)
    }

    @Test
    fun parseAnyAppExtractsMetadataWithoutHttpCheck() {
        // No CFBundleURLTypes — parseBrowser would reject this, but parseAnyApp
        // (used by the manual-add flow) trusts the user's claim that the app is
        // a link handler and just pulls metadata.
        val json = """
            {
              "CFBundleIdentifier": "com.example.tool",
              "CFBundleDisplayName": "Some Tool",
              "CFBundleShortVersionString": "0.9"
            }
        """.trimIndent()

        val browser = parser.parseAnyApp(json, Path("/Applications/Tool.app"))

        assertNotNull(browser)
        assertEquals("com.example.tool", browser.bundleId)
        assertEquals("Some Tool", browser.displayName)
        assertEquals("0.9", browser.version)
    }

    @Test
    fun parseAnyAppReturnsNullOnMalformedJson() {
        assertNull(parser.parseAnyApp("not even json", Path("/Applications/X.app")))
    }

    @Test
    fun parseAnyAppReturnsNullWhenBundleIdentifierMissing() {
        val json = """{"CFBundleName":"X"}"""
        assertNull(parser.parseAnyApp(json, Path("/Applications/X.app")))
    }

    @Test
    fun isLinkHandlerTrueWhenHttpDeclared() {
        val json = """
            {
              "CFBundleIdentifier": "com.example.x",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http", "https"] }
              ]
            }
        """.trimIndent()
        assertEquals(true, parser.isLinkHandler(json))
    }

    @Test
    fun isLinkHandlerFalseWhenNoUrlTypes() {
        assertEquals(false, parser.isLinkHandler("""{"CFBundleIdentifier":"x"}"""))
    }

    @Test
    fun isLinkHandlerFalseOnMalformedJson() {
        assertEquals(false, parser.isLinkHandler("garbage"))
    }

    @Test
    fun skipsNonStringSchemeEntries() {
        // CFBundleURLSchemes contains a number alongside the http string. The number
        // entry must be skipped without throwing; the http entry still qualifies.
        val json = """
            {
              "CFBundleIdentifier": "com.example.numeric",
              "CFBundleName": "Numeric",
              "CFBundleURLTypes": [
                {
                  "CFBundleURLSchemes": [42, "http"]
                }
              ]
            }
        """.trimIndent()

        val browser = parser.parseBrowser(json, Path("/Applications/Numeric.app"))

        assertNotNull(browser)
        assertEquals("com.example.numeric", browser.bundleId)
    }
}
