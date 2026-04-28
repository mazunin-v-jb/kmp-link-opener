package dev.hackathon.linkopener.ui.settings

import androidx.compose.ui.graphics.Color
import dev.hackathon.linkopener.core.model.BrowserId

// TODO: replace with BrowserRepository.observe() from stage 2.
// Until browser-discovery ships, the populated UI is rendered against
// this hardcoded list. The exclude/include toggle is wired to the real
// SettingsRepository so user preferences persist across restarts even
// during the mock period.
internal data class MockBrowser(
    val id: BrowserId,
    val displayName: String,
    val secondaryLabel: String?,
    val accentBackground: Color,
    val accentForeground: Color,
    val initial: String,
    val isSystemDefault: Boolean = false,
)

internal val MockBrowsers: List<MockBrowser> = listOf(
    MockBrowser(
        id = BrowserId("safari"),
        displayName = "Safari 17.4",
        secondaryLabel = null,
        accentBackground = Color(0xFFE3F2FD),
        accentForeground = Color(0xFF0277BD),
        initial = "S",
        isSystemDefault = true,
    ),
    MockBrowser(
        id = BrowserId("chrome"),
        displayName = "Google Chrome 124.0.6367.62",
        secondaryLabel = null,
        accentBackground = Color(0xFFF1F3F4),
        accentForeground = Color(0xFF5F6368),
        initial = "G",
    ),
    MockBrowser(
        id = BrowserId("firefox"),
        displayName = "Firefox 125.0.1",
        secondaryLabel = "Developer Edition",
        accentBackground = Color(0xFFFFF3E0),
        accentForeground = Color(0xFFE65100),
        initial = "F",
    ),
    MockBrowser(
        id = BrowserId("brave"),
        displayName = "Brave 1.65.114",
        secondaryLabel = "Privacy Focused",
        accentBackground = Color(0xFFE8EAF6),
        accentForeground = Color(0xFF1A237E),
        initial = "B",
    ),
    MockBrowser(
        id = BrowserId("arc"),
        displayName = "Arc 1.42",
        secondaryLabel = "Recent Session",
        accentBackground = Color(0xFF263238),
        accentForeground = Color(0xFFFFFFFF),
        initial = "A",
    ),
)
