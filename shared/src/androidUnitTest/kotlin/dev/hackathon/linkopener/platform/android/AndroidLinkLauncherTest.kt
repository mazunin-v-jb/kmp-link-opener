package dev.hackathon.linkopener.platform.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.hackathon.linkopener.core.model.Browser
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for [AndroidLinkLauncher] under Robolectric — exercises
 * the real Android `Intent` + `Context.startActivity` machinery. Robolectric's
 * `ShadowApplication.getNextStartedActivity()` captures the Intent the
 * production code would have dispatched, so we can assert on its action,
 * data, package, and flags.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidLinkLauncherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = AndroidLinkLauncher(context)

    @Test
    fun openInDispatchesActionViewWithUrlAndTargetPackage() = runTest {
        val ok = launcher.openIn(
            browser = browser(applicationPath = "com.android.chrome"),
            url = "https://example.com/foo",
        )

        assertTrue(ok, "openIn should report success when startActivity succeeds")

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull(started, "Expected an Intent to be dispatched")
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.com/foo", started.dataString)
        assertEquals("com.android.chrome", started.`package`)
    }

    @Test
    fun openInAddsNewTaskFlag() = runTest {
        launcher.openIn(browser("org.mozilla.firefox"), "https://example.org")

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull(started)
        // FLAG_ACTIVITY_NEW_TASK is required: the picker activity is about
        // to finish, and starting an activity inside a finishing task
        // would tear it down before the browser comes up.
        assertTrue(
            (started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
            "FLAG_ACTIVITY_NEW_TASK must be set; was 0x${started.flags.toString(16)}",
        )
    }

    @Test
    fun openInPreservesExactUrl() = runTest {
        // URLs with query strings, fragments, and percent-encoded chars
        // must round-trip through the Intent without mangling.
        val url = "https://example.com/path/?q=hello%20world&x=1#frag"
        launcher.openIn(browser("com.android.chrome"), url)

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(url, started?.dataString)
    }

    @Test
    fun openInUsesBrowserApplicationPathAsPackageId() = runTest {
        // applicationPath on Android is overloaded to carry the package
        // name (set by AndroidBrowserDiscovery). Sanity-check that the
        // launcher passes it through verbatim — no path-stripping etc.
        launcher.openIn(browser("io.github.weasel.browser"), "https://x.example")

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals("io.github.weasel.browser", started?.`package`)
    }

    private fun browser(applicationPath: String): Browser = Browser(
        bundleId = applicationPath,
        displayName = applicationPath,
        applicationPath = applicationPath,
        version = null,
        profile = null,
        family = null,
    )
}
