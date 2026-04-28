package dev.hackathon.linkopener.app.tray

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.Component
import java.awt.Window

/**
 * Forces a Java AWT [Window] to render above macOS fullscreen applications.
 *
 * macOS treats fullscreen apps as their own Space; an ordinary
 * `Window.setAlwaysOnTop(true)` only ranks the window at NSFloatingWindowLevel
 * which still sits below a fullscreen app's overlay. To break out of that we
 * need the underlying NSWindow's `collectionBehavior` to include
 * `NSWindowCollectionBehaviorFullScreenAuxiliary`, plus a level high enough
 * to draw over the menu bar (NSStatusWindowLevel).
 *
 * AWT does not expose either property, so we reach through the AWT peer
 * chain via reflection (requires the `--add-opens` JVM args set in
 * desktopApp/build.gradle.kts), then call into libobjc with JNA.
 *
 * No-op on non-macOS hosts and on any reflection failure — the picker still
 * works, just won't overlay fullscreen apps.
 */
object MacOsAlwaysOnTopOverFullScreen {

    // NSWindow level constants (NSWindow.h)
    private const val NS_STATUS_WINDOW_LEVEL = 25L
    // NSWindowCollectionBehavior bit flags (NSWindow.h)
    private const val CAN_JOIN_ALL_SPACES = 1L          // 1 << 0
    private const val FULL_SCREEN_AUXILIARY = 1L shl 8  // 256

    private val isMacOs: Boolean by lazy {
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
    }

    private val objc: ObjC? by lazy {
        if (!isMacOs) null
        else runCatching { Native.load("objc", ObjC::class.java) }.getOrNull()
    }

    fun apply(window: Window) {
        if (!isMacOs) return
        val lib = objc ?: return
        try {
            val nsWindowPtr = nsWindowPointer(window) ?: return
            sendMessage(lib, nsWindowPtr, "setLevel:", NativeLong(NS_STATUS_WINDOW_LEVEL))
            sendMessage(
                lib, nsWindowPtr, "setCollectionBehavior:",
                NativeLong(CAN_JOIN_ALL_SPACES or FULL_SCREEN_AUXILIARY),
            )
        } catch (t: Throwable) {
            System.err.println(
                "[MacOsAlwaysOnTopOverFullScreen] Failed to elevate NSWindow level: ${t.message}. " +
                    "Picker will still appear but may sit below fullscreen apps."
            )
        }
    }

    private fun sendMessage(lib: ObjC, target: Pointer, selector: String, vararg args: Any?) {
        val sel = lib.sel_registerName(selector)
        lib.objc_msgSend(target, sel, *args)
    }

    private fun nsWindowPointer(window: Window): Pointer? {
        // Component.peer is private; AWTAccessor is the supported back-door.
        val accessor = Class.forName("sun.awt.AWTAccessor")
            .getMethod("getComponentAccessor")
            .invoke(null)
        val getPeer = accessor.javaClass.getMethod("getPeer", Component::class.java)
        val peer = getPeer.invoke(accessor, window) ?: return null

        // peer is sun.lwawt.LWWindowPeer
        val getPlatformWindow = peer.javaClass.getMethod("getPlatformWindow")
        val platformWindow = getPlatformWindow.invoke(peer) ?: return null

        // platformWindow is sun.lwawt.macosx.CPlatformWindow extends CFRetainedResource;
        // CFRetainedResource exposes the native NSWindow* through `ptr`.
        val cfRetainedResourceCls = Class.forName("sun.lwawt.macosx.CFRetainedResource")
        val ptrField = cfRetainedResourceCls.getDeclaredField("ptr")
        ptrField.isAccessible = true
        val raw = ptrField.getLong(platformWindow)
        return if (raw == 0L) null else Pointer(raw)
    }

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, vararg args: Any?): Pointer?
        fun sel_registerName(name: String): Pointer
    }
}
