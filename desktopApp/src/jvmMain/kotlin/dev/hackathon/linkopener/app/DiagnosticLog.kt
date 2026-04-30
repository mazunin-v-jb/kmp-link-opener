package dev.hackathon.linkopener.app

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tees `System.out` and `System.err` to a per-run log file at
 * `~/.linkopener/last-run.log`, overwritten each startup.
 *
 * Why a file: a fat-JAR launched by double-click on Linux Mint (and most
 * non-terminal launches generally) has no attached stdout/stderr the user
 * can read, so any `println` / `System.err.println` we emit for diagnostics
 * goes nowhere. A predictable on-disk file the user can `cat` (or send to
 * us) closes that gap without depending on a flag the user has to know
 * about.
 *
 * Implementation: tee at the **OutputStream** layer, not by subclassing
 * PrintStream. PrintStream's internal `print(String)` uses a charset
 * writer that writes through `FilterOutputStream.out` directly, bypassing
 * any `write(...)` overrides on a PrintStream subclass — so a naive
 * `class TeeStream : PrintStream(...)` only catches `write(byte)` from
 * a few code paths and silently drops everything that goes through
 * `print(String)`. Wrapping a fresh PrintStream around a TeeOutputStream
 * sidesteps the problem entirely: PrintStream just forwards to its
 * `out`, which IS the tee.
 */
internal object DiagnosticLog {

    fun installEarly() {
        runCatching {
            val dir = appDir().toFile()
            dir.mkdirs()
            val target = File(dir, "last-run.log")
            // append=false → one log per run, predictable to read.
            val fileStream = FileOutputStream(target, false)

            val originalOut = System.out
            val originalErr = System.err
            // Each tee fans bytes to (original console, file) so the
            // primary stream behavior is preserved for terminal launches.
            // autoFlush=true on the wrapping PrintStream so each println
            // hits disk immediately — diagnostics aren't useful if the
            // app crashes before a buffered flush.
            System.setOut(
                PrintStream(TeeOutputStream(originalOut, fileStream), true, Charsets.UTF_8),
            )
            System.setErr(
                PrintStream(TeeOutputStream(originalErr, fileStream), true, Charsets.UTF_8),
            )
            println("[diagnostic] log started at $target")
        }
    }

    private fun appDir(): Path = Paths.get(System.getProperty("user.home").orEmpty(), ".linkopener")

    /**
     * Fans every byte to two underlying streams. Lives at the
     * OutputStream layer so PrintStream's print()/println() paths route
     * through it cleanly via `FilterOutputStream.out`.
     */
    private class TeeOutputStream(
        private val a: OutputStream,
        private val b: OutputStream,
    ) : OutputStream() {
        override fun write(byteVal: Int) {
            a.write(byteVal)
            b.write(byteVal)
        }

        override fun write(buf: ByteArray, off: Int, len: Int) {
            a.write(buf, off, len)
            b.write(buf, off, len)
        }

        override fun flush() {
            a.flush()
            b.flush()
        }

        override fun close() {
            // Don't close the originals — we don't own them. Closing
            // System.out / System.err on JVM shutdown would defeat any
            // post-shutdown crash diagnostics.
            runCatching { b.flush() }
            runCatching { b.close() }
        }
    }
}
