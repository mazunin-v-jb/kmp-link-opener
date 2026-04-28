package dev.hackathon.linkopener.ui.tray

sealed interface TrayMenuAction {
    data object OpenSettings : TrayMenuAction
    data object Quit : TrayMenuAction
}
