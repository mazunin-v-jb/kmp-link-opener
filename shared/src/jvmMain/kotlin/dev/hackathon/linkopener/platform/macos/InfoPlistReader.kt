package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import java.nio.file.Path
import kotlin.io.path.exists

class InfoPlistReader(
    private val runner: PlutilRunner = PlutilRunner(),
    private val parser: PlistJsonParser = PlistJsonParser(),
) {
    fun readBrowser(appBundlePath: Path): Browser? {
        val plist = appBundlePath.resolve("Contents").resolve("Info.plist")
        if (!plist.exists()) return null
        val json = runner.toJson(plist) ?: return null
        return parser.parseBrowser(json, appBundlePath)
    }
}
