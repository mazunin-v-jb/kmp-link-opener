package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId

/**
 * Best-effort detection of which installed browsers currently have a process
 * running on the host. Used by the picker to grey out icons of stopped
 * browsers so the user sees the running set at a glance.
 *
 * Per-OS semantics live in the implementations:
 * - JVM: enumerates `ps -ax -o comm=` (mac/linux) or PowerShell `Get-Process`
 *   (windows) and matches by executable / `.app` path.
 * - Android: no-op — API 30+ doesn't expose other apps' running state, so
 *   the picker simply doesn't tint anything there.
 */
interface RunningBrowserProbe {
    suspend fun runningOf(installed: List<Browser>): Set<BrowserId>
}

/**
 * Conservative default: report nothing as running. Picker falls back to its
 * "everything fully opaque" rendering, which is the no-information UX.
 */
class NoOpRunningBrowserProbe : RunningBrowserProbe {
    override suspend fun runningOf(installed: List<Browser>): Set<BrowserId> = emptySet()
}
