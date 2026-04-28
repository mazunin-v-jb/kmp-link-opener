package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppInfo
import dev.hackathon.linkopener.domain.repository.AppInfoRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetAppInfoUseCaseTest {

    @Test
    fun returnsAppInfoFromRepository() {
        val expected = AppInfo(name = "Test App", version = "9.9.9")
        val useCase = GetAppInfoUseCase(FakeRepository(expected))

        assertEquals(expected, useCase())
    }

    @Test
    fun returnsNonBlankNameAndVersion() {
        val real = GetAppInfoUseCase(FakeRepository(AppInfo("Link Opener", "0.1.0")))

        val info = real()

        assertTrue(info.name.isNotBlank(), "name should not be blank")
        assertTrue(info.version.isNotBlank(), "version should not be blank")
    }

    private class FakeRepository(private val value: AppInfo) : AppInfoRepository {
        override fun getAppInfo(): AppInfo = value
    }
}
