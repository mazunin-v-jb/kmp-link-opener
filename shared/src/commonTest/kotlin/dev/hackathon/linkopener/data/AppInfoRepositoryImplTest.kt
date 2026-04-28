package dev.hackathon.linkopener.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppInfoRepositoryImplTest {

    @Test
    fun returnsHardcodedNameAndGeneratedVersion() {
        val info = AppInfoRepositoryImpl().getAppInfo()

        assertEquals("Link Opener", info.name)
        // Don't assert a literal value here — the version is generated from
        // root `gradle.properties` via the :shared `generateBuildVersion`
        // task. Bumping the property shouldn't break this test. We only check
        // the shape (MAJOR.MINOR.PATCH with MAJOR ≥ 1, the DMG-packager
        // constraint also documented in CLAUDE.md).
        val semverWithMajorAtLeastOne = Regex("^[1-9][0-9]*\\.[0-9]+\\.[0-9]+$")
        assertTrue(
            info.version.matches(semverWithMajorAtLeastOne),
            "version '${info.version}' does not match MAJOR.MINOR.PATCH with MAJOR ≥ 1",
        )
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
