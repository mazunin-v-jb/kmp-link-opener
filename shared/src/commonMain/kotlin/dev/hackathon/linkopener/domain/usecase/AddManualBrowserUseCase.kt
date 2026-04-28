package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class AddManualBrowserUseCase(
    private val extractor: BrowserMetadataExtractor,
    private val settings: SettingsRepository,
    private val browsers: BrowserRepository,
    private val ownBundleId: String,
) {
    suspend operator fun invoke(path: String): AddResult {
        val extracted = extractor.extract(path)
        val browser = when (extracted) {
            is BrowserMetadataExtractor.ExtractResult.Success -> extracted.browser
            is BrowserMetadataExtractor.ExtractResult.Failure ->
                return AddResult.InvalidApp(extracted.reason)
        }
        if (browser.bundleId == ownBundleId) {
            return AddResult.IsSelf
        }
        // Check against the merged list (discovered + already-manual) so we
        // can't shadow an OS-discovered Safari by adding /Applications/Safari.app
        // by hand, and so a second click on an already-added manual entry is
        // rejected too.
        val knownPaths = browsers.getInstalledBrowsers().mapTo(HashSet()) { it.applicationPath }
        if (browser.applicationPath in knownPaths) {
            return AddResult.Duplicate
        }
        settings.addManualBrowser(browser)
        return AddResult.Added(browser)
    }

    sealed interface AddResult {
        data class Added(val browser: Browser) : AddResult
        data object Duplicate : AddResult
        data object IsSelf : AddResult
        data class InvalidApp(val reason: String) : AddResult
    }
}
