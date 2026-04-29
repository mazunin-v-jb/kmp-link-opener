package dev.hackathon.linkopener.platform.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Robolectric-driven test for [AndroidDefaultBrowserService]. Robolectric's
 * `ShadowPackageManager.addResolveInfoForIntent` lets us seed which package
 * the system would resolve `ACTION_VIEW http://…` to, so the
 * `isDefaultBrowser` comparison is exercised against realistic data.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidDefaultBrowserServiceTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun isDefaultBrowserReturnsTrueWhenOurPackageResolvesAsDefault() = runTest {
        seedDefaultResolveTo(application.packageName)

        val service = AndroidDefaultBrowserService(
            context = application,
            ownPackageName = application.packageName,
        )

        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserReturnsFalseWhenAnotherPackageResolvesAsDefault() = runTest {
        seedDefaultResolveTo("com.android.chrome")

        val service = AndroidDefaultBrowserService(
            context = application,
            ownPackageName = application.packageName,
        )

        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserReturnsFalseWhenNothingResolves() = runTest {
        // No resolve info seeded — packageManager.resolveActivity should
        // return null, and we should treat that as "not the default" not
        // as an error.
        val service = AndroidDefaultBrowserService(
            context = application,
            ownPackageName = application.packageName,
        )

        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun canOpenSystemSettingsIsTrue() {
        val service = AndroidDefaultBrowserService(application, application.packageName)
        // Android always exposes the Default Apps settings page on every
        // supported API level — unconditionally true (Linux is the only
        // platform that returns false).
        assertTrue(service.canOpenSystemSettings)
    }

    @Test
    fun openSystemSettingsLaunchesAnIntent() = runTest {
        // The exact intent action depends on API level / role availability:
        //   * API 29+ with ROLE_BROWSER available  → RoleManager request
        //     intent (action varies by Android version, package targets
        //     the role controller).
        //   * Anything else                        → ACTION_MANAGE_DEFAULT_APPS_SETTINGS.
        // We don't pin the exact action — Robolectric's runtime version
        // changes which path fires. Instead, assert the contract:
        //   1. `openSystemSettings` reports success.
        //   2. *Some* intent was dispatched.
        //   3. FLAG_ACTIVITY_NEW_TASK is on it (required from a non-Activity Context).
        val service = AndroidDefaultBrowserService(application, application.packageName)

        val ok = service.openSystemSettings()

        assertTrue(ok)
        val started = shadowOf(application).nextStartedActivity
        assertNotNull(started)
        assertTrue((started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    /**
     * Wires Robolectric's `ShadowPackageManager` so `resolveActivity` for
     * `ACTION_VIEW http://example.com` returns an entry pointing at the
     * given package — i.e. that package is the system's default browser.
     */
    private fun seedDefaultResolveTo(packageName: String) {
        val pm = shadowOf(application.packageManager)
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.MainActivity"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = packageName
                }
            }
            // MATCH_DEFAULT_ONLY filters by hasCategory(DEFAULT) — the
            // resolve info advertises the DEFAULT category via its filter.
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            }
            isDefault = true
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        pm.addResolveInfoForIntent(intent, resolveInfo)
        // Component activation so PackageManager treats the activity as
        // installed (not just declared).
        pm.installPackage(buildPackageInfo(packageName))
    }

    private fun buildPackageInfo(packageName: String): android.content.pm.PackageInfo =
        android.content.pm.PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                this.packageName = packageName
            }
        }
}
