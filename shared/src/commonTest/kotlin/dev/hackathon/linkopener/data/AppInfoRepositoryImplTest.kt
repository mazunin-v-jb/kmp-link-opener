package dev.hackathon.linkopener.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppInfoRepositoryImplTest {

    @Test
    fun returnsHardcodedAppInfo() {
        val info = AppInfoRepositoryImpl().getAppInfo()

        assertEquals("Link Opener", info.name)
        assertEquals("0.1.0", info.version)
    }

    @Test
    fun returnsSameValueOnEachCall() {
        val repo = AppInfoRepositoryImpl()

        val first = repo.getAppInfo()
        val second = repo.getAppInfo()

        assertEquals(first, second)
    }

    @Test
    fun nameAndVersionAreNonBlank() {
        val info = AppInfoRepositoryImpl().getAppInfo()

        assertTrue(info.name.isNotBlank())
        assertTrue(info.version.isNotBlank())
    }
}
