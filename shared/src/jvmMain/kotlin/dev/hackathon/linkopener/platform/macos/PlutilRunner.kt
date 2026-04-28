package dev.hackathon.linkopener.platform.macos

import java.nio.file.Path
import java.util.concurrent.TimeUnit

open class PlutilRunner(
    private val executable: String = "/usr/bin/plutil",
    private val timeoutSeconds: Long = 5,
) {
    open fun toJson(plistPath: Path): String? {
        val process = try {
            ProcessBuilder(executable, "-convert", "json", "-o", "-", plistPath.toString())
                .redirectErrorStream(false)
                .start()
        } catch (_: Exception) {
            return null
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        return output.takeIf { it.isNotBlank() }
    }
}
