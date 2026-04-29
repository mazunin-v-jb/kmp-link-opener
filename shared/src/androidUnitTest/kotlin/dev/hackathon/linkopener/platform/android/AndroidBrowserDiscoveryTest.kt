package dev.hackathon.linkopener.platform.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Robolectric-driven test for [AndroidBrowserDiscovery]. Seeds
 * `ShadowPackageManager` with fake browser packages and asserts the
 * discovery result faithfully reflects what `queryIntentActivities`
 * returns: filtered, deduped, sorted by display name.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBrowserDiscoveryTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun returnsEmptyListWhenNoBrowsersInstalled() = runTest {
        val discovery = AndroidBrowserDiscovery(application, ownPackageName = "test.self")
        assertEquals(emptyList(), discovery.discover())
    }

    @Test
    fun mapsResolveInfoToBrowserModelWithPackageId() = runTest {
        seedBrowser("com.android.chrome", label = "Chrome", versionName = "120.0")
        seedBrowser("org.mozilla.firefox", label = "Firefox", versionName = "115.0")

        val discovery = AndroidBrowserDiscovery(application, ownPackageName = "test.self")
        val browsers = discovery.discover()

        assertEquals(2, browsers.size)

        // Sorted alphabetically by displayName.
        val chrome = browsers[0]
        assertEquals("Chrome", chrome.displayName)
        assertEquals("com.android.chrome", chrome.applicationPath)
        assertEquals("com.android.chrome", chrome.bundleId)
        assertEquals("120.0", chrome.version)

        val firefox = browsers[1]
        assertEquals("Firefox", firefox.displayName)
        assertEquals("org.mozilla.firefox", firefox.applicationPath)
    }

    @Test
    fun filtersOutOwnPackageName() = runTest {
        // We declare a self-handler in our manifest, so we'd otherwise
        // appear in our own picker — and clicking ourselves would loop.
        seedBrowser("com.android.chrome", label = "Chrome")
        seedBrowser("dev.hackathon.linkopener", label = "Link Opener")

        val discovery = AndroidBrowserDiscovery(
            application,
            ownPackageName = "dev.hackathon.linkopener",
        )
        val browsers = discovery.discover()

        assertEquals(1, browsers.size)
        assertEquals("Chrome", browsers.single().displayName)
        assertFalse(browsers.any { it.applicationPath == "dev.hackathon.linkopener" })
    }

    @Test
    fun dedupesByPackageNameWhenMultipleResolveInfosShareIt() = runTest {
        // Real-world: a browser may declare multiple <activity> entries
        // (e.g. one for http, one for https, one for custom tabs). Each
        // registers a separate ResolveInfo with the same package id.
        // Our discovery should fold them into one Browser entry.
        val pm = shadowOf(application.packageManager)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        pm.addResolveInfoForIntent(intent, makeResolveInfo("dup.browser", "Browser", "1.0", "ActivityA"))
        pm.addResolveInfoForIntent(intent, makeResolveInfo("dup.browser", "Browser", "1.0", "ActivityB"))
        pm.installPackage(makePackageInfo("dup.browser", "1.0"))

        val discovery = AndroidBrowserDiscovery(application, ownPackageName = "test.self")
        val browsers = discovery.discover()

        assertEquals(1, browsers.size)
    }

    @Test
    fun sortsByDisplayNameCaseInsensitive() = runTest {
        // Mixed case in the labels should still produce a deterministic
        // sort. "FIREFOX" lowercases to "firefox" which sorts after
        // "chrome" — matches what users see in the picker.
        seedBrowser("a.pkg", label = "FIREFOX", versionName = null)
        seedBrowser("b.pkg", label = "chrome", versionName = null)

        val discovery = AndroidBrowserDiscovery(application, ownPackageName = "test.self")
        val labels = discovery.discover().map { it.displayName }

        assertEquals(listOf("chrome", "FIREFOX"), labels)
    }

    @Test
    fun fallsBackToPackageNameWhenLabelIsBlank() = runTest {
        // A browser with a blank label (rare but possible if the install
        // is broken) should at least surface a stable identifier so the
        // user can pick it.
        seedBrowser("io.weasel.browser", label = "", versionName = null)

        val discovery = AndroidBrowserDiscovery(application, ownPackageName = "test.self")
        val browser = discovery.discover().single()

        assertEquals("io.weasel.browser", browser.displayName)
    }

    /**
     * Wires a fake browser package into Robolectric's PackageManager:
     *   1. `addResolveInfoForIntent` so `queryIntentActivities(http)` returns it.
     *   2. `installPackage` so `getPackageInfo(packageName)` returns the version.
     */
    private fun seedBrowser(packageName: String, label: String, versionName: String? = null) {
        val pm = shadowOf(application.packageManager)
        val resolveInfo = makeResolveInfo(packageName, label, versionName, "$packageName.MainActivity")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        pm.addResolveInfoForIntent(intent, resolveInfo)
        pm.installPackage(makePackageInfo(packageName, versionName))
    }

    private fun makeResolveInfo(
        packageName: String,
        label: String,
        versionName: String?,
        activityName: String,
    ): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = label
            }
            nonLocalizedLabel = label
        }
        // Set display label directly. Robolectric's loadLabel reads from
        // ActivityInfo / ApplicationInfo; the nonLocalizedLabel above
        // gives us a deterministic value without resource lookup.
        nonLocalizedLabel = label
        filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("http")
            addDataScheme("https")
        }
        isDefault = true
    }

    private fun makePackageInfo(packageName: String, versionName: String?): PackageInfo =
        PackageInfo().apply {
            this.packageName = packageName
            this.versionName = versionName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
            }
        }
}
