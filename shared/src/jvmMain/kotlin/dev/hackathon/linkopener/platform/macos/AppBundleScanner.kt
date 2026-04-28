package dev.hackathon.linkopener.platform.macos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class AppBundleScanner {

    fun findAppBundles(root: Path, maxDepth: Int = 2): List<Path> {
        if (!root.isDirectory()) return emptyList()
        val results = mutableListOf<Path>()
        scan(root, depth = 0, maxDepth = maxDepth, results = results)
        return results
    }

    private fun scan(dir: Path, depth: Int, maxDepth: Int, results: MutableList<Path>) {
        if (depth >= maxDepth) return
        val entries = try {
            Files.newDirectoryStream(dir).use { it.toList() }
        } catch (_: Exception) {
            return
        }
        for (entry in entries) {
            if (!entry.isDirectory()) continue
            if (entry.name.endsWith(".app")) {
                results.add(entry)
            } else {
                scan(entry, depth + 1, maxDepth, results)
            }
        }
    }
}
