package dev.hackathon.linkopener.ui.strings

import androidx.compose.runtime.Composable
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.platform.HostOs
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.default_browser_instructions_android
import kmp_link_opener.shared.generated.resources.default_browser_instructions_linux
import kmp_link_opener.shared.generated.resources.default_browser_instructions_macos
import kmp_link_opener.shared.generated.resources.default_browser_instructions_windows
import kmp_link_opener.shared.generated.resources.default_browser_unsupported_os
import kmp_link_opener.shared.generated.resources.language_english_native
import kmp_link_opener.shared.generated.resources.language_russian_native
import kmp_link_opener.shared.generated.resources.language_system
import kmp_link_opener.shared.generated.resources.theme_dark
import kmp_link_opener.shared.generated.resources.theme_light
import kmp_link_opener.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.System -> stringResource(Res.string.theme_system)
    AppTheme.Light -> stringResource(Res.string.theme_light)
    AppTheme.Dark -> stringResource(Res.string.theme_dark)
}

// "System" follows the current UI language; English/Russian stay in their
// native form regardless of UI language — matches macOS / browser convention.
@Composable
fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> stringResource(Res.string.language_system)
    AppLanguage.En -> stringResource(Res.string.language_english_native)
    AppLanguage.Ru -> stringResource(Res.string.language_russian_native)
}

@Composable
fun defaultBrowserInstructions(os: HostOs): List<String> = when (os) {
    HostOs.MacOs -> stringArrayResource(Res.array.default_browser_instructions_macos)
    HostOs.Windows -> stringArrayResource(Res.array.default_browser_instructions_windows)
    HostOs.Linux -> stringArrayResource(Res.array.default_browser_instructions_linux)
    HostOs.Android -> stringArrayResource(Res.array.default_browser_instructions_android)
    HostOs.Other -> listOf(stringResource(Res.string.default_browser_unsupported_os))
}
