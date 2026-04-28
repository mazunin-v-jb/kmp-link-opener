package dev.hackathon.linkopener.data

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.platform.AutoStartManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SettingsRepositoryImplTest {

    @Test
    fun emitsDefaultsWhenStoreIsEmpty() = runTest {
        val repo = newRepo()

        assertEquals(AppSettings.Default, repo.settings.value)
    }

    @Test
    fun updateThemePersistsAndEmits() = runTest {
        val store = FakeSettings()
        val repo = newRepo(store = store)

        repo.updateTheme(AppTheme.Dark)

        assertEquals(AppTheme.Dark, repo.settings.value.theme)
        assertEquals("Dark", store.getStringOrNull("settings.theme"))
    }

    @Test
    fun updateLanguagePersistsAndEmits() = runTest {
        val store = FakeSettings()
        val repo = newRepo(store = store)

        repo.updateLanguage(AppLanguage.Ru)

        assertEquals(AppLanguage.Ru, repo.settings.value.language)
        assertEquals("Ru", store.getStringOrNull("settings.language"))
    }

    @Test
    fun setAutoStartPropagatesToManagerAndStore() = runTest {
        val manager = RecordingAutoStartManager()
        val store = FakeSettings()
        val repo = newRepo(store = store, autoStartManager = manager)

        repo.setAutoStart(true)

        assertTrue(repo.settings.value.autoStartEnabled)
        assertEquals(true, store.getBoolean("settings.autoStart", false))
        assertEquals(listOf(true), manager.calls)

        repo.setAutoStart(false)

        assertFalse(repo.settings.value.autoStartEnabled)
        assertEquals(false, store.getBoolean("settings.autoStart", true))
        assertEquals(listOf(true, false), manager.calls)
    }

    @Test
    fun setBrowserExcludedTogglesMembership() = runTest {
        val store = FakeSettings()
        val repo = newRepo(store = store)
        val safari = BrowserId("safari")
        val firefox = BrowserId("firefox")

        repo.setBrowserExcluded(safari, true)
        repo.setBrowserExcluded(firefox, true)

        assertEquals(setOf(safari, firefox), repo.settings.value.excludedBrowserIds)

        repo.setBrowserExcluded(safari, false)
        assertEquals(setOf(firefox), repo.settings.value.excludedBrowserIds)
    }

    @Test
    fun setBrowserExcludedIsIdempotent() = runTest {
        val repo = newRepo()
        val id = BrowserId("safari")

        repo.setBrowserExcluded(id, true)
        repo.setBrowserExcluded(id, true)

        assertEquals(setOf(id), repo.settings.value.excludedBrowserIds)
    }

    @Test
    fun loadsPersistedStateOnConstruction() = runTest {
        val store = FakeSettings()
        store.putString("settings.theme", "Light")
        store.putString("settings.language", "En")
        store.putBoolean("settings.autoStart", true)
        store.putString("settings.exclusions", """["chrome","brave"]""")
        store.putString("settings.browserOrder", """["safari","chrome"]""")

        val repo = newRepo(store = store)

        assertEquals(
            AppSettings(
                theme = AppTheme.Light,
                language = AppLanguage.En,
                autoStartEnabled = true,
                excludedBrowserIds = setOf(BrowserId("chrome"), BrowserId("brave")),
                browserOrder = listOf(BrowserId("safari"), BrowserId("chrome")),
            ),
            repo.settings.value,
        )
    }

    @Test
    fun setBrowserOrderPersistsList() = runTest {
        val store = FakeSettings()
        val repo = newRepo(store = store)
        val order = listOf(BrowserId("safari"), BrowserId("chrome"), BrowserId("firefox"))

        repo.setBrowserOrder(order)

        assertEquals(order, repo.settings.value.browserOrder)
        assertEquals("""["safari","chrome","firefox"]""", store.getStringOrNull("settings.browserOrder"))
    }

    @Test
    fun setBrowserOrderIsIdempotent() = runTest {
        val store = FakeSettings()
        val repo = newRepo(store = store)
        val order = listOf(BrowserId("a"), BrowserId("b"))

        repo.setBrowserOrder(order)
        // A second call with the same value should be a no-op (no extra emission, no rewrite).
        repo.setBrowserOrder(order)

        assertEquals(order, repo.settings.value.browserOrder)
    }

    @Test
    fun corruptedBrowserOrderJsonFallsBackToEmpty() = runTest {
        val store = FakeSettings()
        store.putString("settings.browserOrder", "broken")

        val repo = newRepo(store = store)

        assertEquals(emptyList(), repo.settings.value.browserOrder)
    }

    @Test
    fun corruptedExclusionsJsonFallsBackToEmpty() = runTest {
        val store = FakeSettings()
        store.putString("settings.exclusions", "not-a-json")

        val repo = newRepo(store = store)

        assertEquals(emptySet(), repo.settings.value.excludedBrowserIds)
    }

    @Test
    fun unknownEnumNameFallsBackToDefault() = runTest {
        val store = FakeSettings()
        store.putString("settings.theme", "Neon")

        val repo = newRepo(store = store)

        assertEquals(AppTheme.System, repo.settings.value.theme)
    }

    private fun newRepo(
        store: Settings = FakeSettings(),
        autoStartManager: AutoStartManager = RecordingAutoStartManager(),
    ): SettingsRepositoryImpl = SettingsRepositoryImpl(
        store = store,
        json = Json { ignoreUnknownKeys = true },
        autoStartManager = autoStartManager,
    )

    private class RecordingAutoStartManager : AutoStartManager {
        val calls = mutableListOf<Boolean>()
        override suspend fun setEnabled(enabled: Boolean) {
            calls += enabled
        }

        override suspend fun isEnabled(): Boolean = calls.lastOrNull() ?: false
    }
}
