package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WindowsDefaultBrowserService : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    // TODO: real check via HKCU\Software\Microsoft\Windows\Shell\Associations\
    //  UrlAssociations\http\UserChoice\ProgId once the app installs an MSI
    //  with a registered ProgId (stage 7). Until then the answer is always
    //  "no, I'm not the default".
    override suspend fun isDefaultBrowser(): Boolean = false

    override suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        // ms-settings:defaultapps deep-links into Settings → Apps → Default apps.
        runCatching {
            ProcessBuilder("cmd", "/c", "start", "ms-settings:defaultapps")
                .inheritIO()
                .start()
                .waitFor()
        }.isSuccess
    }
}
