package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId

/**
 * Best-effort detection of which installed browsers currently have a process
 * running on the host. Used by the picker to grey out icons of stopped
 * browsers so the user sees the running set at a glance.
 *
 * Three-state return:
 *  - `null` — probing is **not supported** on this host (Android, where
 *    API 30+ refuses other-app process queries). Picker leaves every row
 *    fully opaque.
 *  - empty set — probe ran successfully and matched nothing. Picker fades
 *    every row and shows "(not running)".
 *  - non-empty set — exactly the matched browser ids.
 *
 * Per-OS semantics live in the implementations:
 * - JVM mac: `ps -ax -o comm=` matched against `.app/Contents/MacOS/` paths.
 * - JVM windows: PowerShell `Get-Process` matched by `.exe` path.
 * - JVM linux: `/proc/<pid>/cmdline` matched against the binary resolved
 *   from `.desktop`'s `Exec=`, with basename fallback for wrapper scripts.
 */
interface RunningBrowserProbe {
    suspend fun runningOf(installed: List<Browser>): Set<BrowserId>?
}

/**
 * Conservative default: report `null` (unsupported). Picker falls back to
 * its "everything fully opaque" rendering, which is the no-information UX
 * appropriate when we genuinely can't tell.
 */
class NoOpRunningBrowserProbe : RunningBrowserProbe {
    override suspend fun runningOf(installed: List<Browser>): Set<BrowserId>? = null
}
