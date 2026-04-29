package dev.hackathon.linkopener.app

import android.app.Application

/**
 * Holds the shared `AndroidAppContainer` for the whole process so both
 * `MainActivity` (Settings) and `PickerActivity` (URL picker) wire to the
 * same DI graph + cached state. Discovery prefetch runs on construction
 * (in the container's `init`) so the first picker launch finds browsers
 * and icons already cached.
 */
class LinkOpenerApplication : Application() {

    lateinit var container: AndroidAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AndroidAppContainer(applicationContext)
    }
}
