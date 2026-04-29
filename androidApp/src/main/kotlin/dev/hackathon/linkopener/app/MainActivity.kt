package dev.hackathon.linkopener.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.ui.settings.SettingsScreen
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme

/**
 * Hosts `SettingsScreen` as the launcher / settings entry-point. Pulled
 * straight from `:shared/commonMain` — same Composable the desktop app
 * uses, no Android-specific UI added.
 *
 * Locale management is desktop-only for now (we set JVM `Locale.getDefault`
 * on language change so Compose Resources picks up the right XML); the
 * AndroidAppContainer's `applyLocale` is a no-op. Android string changes
 * follow the system locale.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as LinkOpenerApplication).container }

    override fun onResume() {
        super.onResume()
        // Returning from RoleManager dialog / Default Apps page → re-read
        // the system default-browser binding so the Settings banner
        // updates without an app restart. Cheap (single PackageManager
        // call) so we don't gate it on whether the user actually went
        // to the dialog.
        container.recheckDefaultBrowser()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appVersion = container.getAppInfoUseCase().version

        setContent {
            val viewModel = remember { container.newSettingsViewModel() }
            val settings by container.getSettingsFlowUseCase().collectAsState()

            LinkOpenerTheme(theme = settings.theme) {
                SettingsScreen(
                    viewModel = viewModel,
                    appVersion = appVersion,
                    currentOs = HostOs.Android,
                    onCloseRequest = { finish() },
                )
            }
        }
    }
}
