package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnsupportedManualBrowserExtractorTest {

    @Test
    fun extractAlwaysReturnsFailureWithOsName() = runTest {
        val extractor = UnsupportedManualBrowserExtractor(osName = "Plan 9")

        val result = extractor.extract("/anything")

        val failure = assertIs<ExtractResult.Failure>(result)
        assertTrue(failure.reason.contains("Plan 9"), "expected reason to mention OS name; got: ${failure.reason}")
    }

    @Test
    fun extractFallsBackWhenOsNameBlank() = runTest {
        val extractor = UnsupportedManualBrowserExtractor(osName = "")

        val result = extractor.extract("/anything")

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("this platform"))
    }
}
