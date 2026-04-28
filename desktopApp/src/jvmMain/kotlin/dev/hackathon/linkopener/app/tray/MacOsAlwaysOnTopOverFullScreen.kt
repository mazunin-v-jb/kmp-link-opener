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
 * to draw above the menu bar (NSStatusWindowLevel).
 *
 * AWT does not expose either property, so we reach through the AWT peer
 * chain via reflection (requires the `--add-opens` JVM args set in
 * desktopApp/build.gradle.kts), then call into libobjc with JNA.
 *
 * Logs each step so misbehaviour is debuggable from gradle/IDEA console.
 */
object MacOsAlwaysOnTopOverFullScreen {

    // NSWindow level constants (NSWindow.h)
    private const val NS_STATUS_WINDOW_LEVEL = 25L
    // NSWindowCollectionBehavior bit flags (NSWindow.h)
    private const val CAN_JOIN_ALL_SPACES = 1L          // 1 << 0
    private const val FULL_SCREEN_AUXILIARY = 1L shl 8  // 256

    private const val TAG = "[picker.fullscreen]"

    private val isMacOs: Boolean by lazy {
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
    }

    private val objc: ObjC? by lazy {
        if (!isMacOs) null
        else runCatching { Native.load("objc", ObjC::class.java) }
            .onFailure { println("$TAG failed to load libobjc: ${it.message}") }
            .getOrNull()
    }

    fun apply(window: Window) {
        if (!isMacOs) {
            println("$TAG skipped (host is not macOS)")
            return
        }
        val lib = objc ?: return

        val nsWindowPtr = try {
            nsWindowPointer(window)
        } catch (t: Throwable) {
            println("$TAG reflection failed: ${t::class.simpleName}: ${t.message}")
            return
        }
        if (nsWindowPtr == null) {
            println("$TAG NSWindow pointer is null (peer not attached?)")
            return
        }

        try {
            val setLevelSel = lib.sel_registerName("setLevel:")
            val setBehaviorSel = lib.sel_registerName("setCollectionBehavior:")
            lib.objc_msgSend(nsWindowPtr, setLevelSel, NativeLong(NS_STATUS_WINDOW_LEVEL))
            lib.objc_msgSend(
                nsWindowPtr,
                setBehaviorSel,
                NativeLong(CAN_JOIN_ALL_SPACES or FULL_SCREEN_AUXILIARY),
            )
            println(
                "$TAG applied — level=$NS_STATUS_WINDOW_LEVEL, " +
                    "behavior=${CAN_JOIN_ALL_SPACES or FULL_SCREEN_AUXILIARY}, " +
                    "ptr=0x${java.lang.Long.toHexString(Pointer.nativeValue(nsWindowPtr))}",
            )
        } catch (t: Throwable) {
            println("$TAG objc_msgSend failed: ${t::class.simpleName}: ${t.message}")
        }
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

    /**
     * Explicit (non-vararg) signature for objc_msgSend. JNA's vararg dispatch
     * doesn't match the arm64 macOS calling convention reliably for NSInteger
     * arguments — declaring the exact signature keeps it sane.
     *
     * Both setLevel: and setCollectionBehavior: take a single NSInteger /
     * NSUInteger (== signed/unsigned long on 64-bit Apple) and return void;
     * one signature covers both.
     */
    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: NativeLong): Pointer?
        fun sel_registerName(name: String): Pointer
    }
}
