package dev.hackathon.linkopener.app

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-platform single-instance enforcement.
 *
 * Two coordinated artefacts under [appDir]:
 * - `instance.lock` — exclusive `FileChannel.tryLock()`. Decides primary vs
 *   secondary. Backed by `fcntl` on macOS/Linux, `LockFileEx` on Windows; the
 *   OS releases the lock on process death (including SIGKILL), so a crashed
 *   primary never permanently wedges a future startup.
 * - `instance.port` — TCP port of the primary's loopback `ServerSocket`.
 *   Secondary reads it, connects, optionally sends a UTF-8 line, exits.
 *   Port `0` means the OS picks a free port, so we don't fight with anything
 *   else on the loopback interface.
 *
 * Activation protocol (newline-terminated, UTF-8):
 * - Empty line (`\n`) — "activate me" (Settings should pop / nudge).
 * - `<url>\n` — "open this URL in the picker". Used on Windows when a
 *   secondary process was spawned by Windows' URL handler with the URL
 *   on argv; the secondary forwards to the primary, then exits.
 *
 * macOS doesn't use the URL payload — Launch Services routes URLs to the
 * already-running primary via the OpenURIHandler before any second JVM
 * spawns. Linux is stage 8.
 */
class SingleInstanceGuard private constructor(
    private val lockFile: RandomAccessFile,
    private val lock: FileLock,
    private val serverSocket: ServerSocket,
    private val portFile: Path,
) {
    /**
     * Invoked when a secondary instance pings us. The String argument
     * is the URL the secondary wants opened, or null when the secondary
     * sent only the "activate me" signal (empty line).
     */
    @Volatile
    var onActivationRequest: (url: String?) -> Unit = {}

    private val released = AtomicBoolean(false)
    private val listenerThread: Thread = Thread(::runListener, "single-instance-listener").apply {
        isDaemon = true
        start()
    }

    private fun runListener() {
        while (!serverSocket.isClosed) {
            val client: Socket = try {
                serverSocket.accept()
            } catch (_: SocketException) {
                // serverSocket.close() during release() trips this — clean exit.
                break
            } catch (_: IOException) {
                // Anything else: don't let one bad connection kill the loop.
                continue
            }
            val payload: String? = try {
                client.use {
                    it.soTimeout = ACCEPT_DRAIN_TIMEOUT_MS
                    val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
                    runCatching { reader.readLine() }.getOrNull()
                }
            } catch (_: IOException) {
                null
            }
            // Empty / null payload = bare activation; non-empty = URL.
            val url = payload?.takeIf { it.isNotBlank() }
            runCatching { onActivationRequest(url) }.onFailure { t ->
                System.err.println("[single-instance] activation handler threw: ${t.message}")
            }
        }
    }

    /**
     * Releases the lock and closes the listener. Idempotent. Test-only —
     * production runs hold the guard until JVM exit, at which point the OS
     * cleans up.
     */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { serverSocket.close() }
        runCatching { lock.release() }
        runCatching { lockFile.close() }
        runCatching { Files.deleteIfExists(portFile) }
        // Listener thread is daemon and breaks out of accept() on socket close.
        // Joining is best-effort so tests don't have to wait on a hung thread.
        runCatching { listenerThread.join(LISTENER_JOIN_TIMEOUT_MS) }
    }

    companion object {
        private const val LOCK_FILE_NAME = "instance.lock"
        private const val PORT_FILE_NAME = "instance.port"
        private const val APP_DIR_NAME = ".linkopener"
        private const val SIGNAL_CONNECT_TIMEOUT_MS = 1_000
        private const val ACCEPT_DRAIN_TIMEOUT_MS = 500
        private const val LISTENER_JOIN_TIMEOUT_MS = 500L
        private const val SOCKET_BACKLOG = 4

        /**
         * Attempts to become the primary instance.
         *
         * - Returns a non-null guard if we got the lock; caller binds
         *   [onActivationRequest] and keeps the reference for the lifetime of
         *   the JVM.
         * - Returns `null` if another live instance holds the lock; in that
         *   case this method has already pinged the primary (with [urlPayload]
         *   if provided) so it can react, and the caller should exit cleanly.
         *
         * @param urlPayload optional URL to forward to the primary on the
         * "we're a secondary" path. Used when this JVM was spawned by the
         * Windows URL handler with the URL on argv — the primary picks it
         * up, opens the picker, and the secondary exits cleanly.
         *
         * Failure modes: if creating [appDir], opening the lock file, or
         * binding the server socket throws, the exception propagates. Those
         * failures indicate a real environmental problem (read-only home,
         * loopback interface unavailable) and are louder if surfaced than
         * silently degraded.
         */
        fun acquireOrSignal(
            appDir: Path = defaultAppDir(),
            urlPayload: String? = null,
        ): SingleInstanceGuard? {
            Files.createDirectories(appDir)
            val lockPath = appDir.resolve(LOCK_FILE_NAME)
            val portPath = appDir.resolve(PORT_FILE_NAME)

            val raf = RandomAccessFile(lockPath.toFile(), "rw")
            val lock: FileLock? = try {
                raf.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // Same JVM already holds the lock (shouldn't happen in prod —
                // main() calls this exactly once — but defensive).
                null
            } catch (_: IOException) {
                null
            }

            if (lock == null) {
                // We're the secondary. Ping the primary best-effort and bail.
                runCatching { signalPrimary(portPath, urlPayload) }
                runCatching { raf.close() }
                return null
            }

            val server = try {
                ServerSocket(0, SOCKET_BACKLOG, InetAddress.getLoopbackAddress())
            } catch (t: IOException) {
                runCatching { lock.release() }
                runCatching { raf.close() }
                throw t
            }

            try {
                Files.writeString(
                    portPath,
                    server.localPort.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            } catch (t: IOException) {
                runCatching { server.close() }
                runCatching { lock.release() }
                runCatching { raf.close() }
                throw t
            }

            return SingleInstanceGuard(raf, lock, server, portPath)
        }

        private fun signalPrimary(portFile: Path, urlPayload: String?) {
            if (!Files.exists(portFile)) return
            val port = runCatching { Files.readString(portFile).trim().toInt() }
                .getOrNull()
                ?: return
            if (port !in 1..65535) return
            Socket().use { sock ->
                sock.connect(
                    InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                    SIGNAL_CONNECT_TIMEOUT_MS,
                )
                // Always end with newline. URL-bearing payload writes the
                // URL bytes first; bare activation sends just the newline.
                // The primary's listener reads one line via BufferedReader.
                sock.getOutputStream().apply {
                    if (urlPayload != null && urlPayload.isNotBlank()) {
                        write(urlPayload.toByteArray(StandardCharsets.UTF_8))
                    }
                    write('\n'.code)
                    flush()
                }
            }
        }

        private fun defaultAppDir(): Path =
            Path.of(System.getProperty("user.home"), APP_DIR_NAME)
    }
}
