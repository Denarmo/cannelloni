package dev.cannelloni.scorza.setup

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SetupCoordinatorTest {

    private val coordinator = SetupCoordinator(mockk<Context>(relaxed = true))

    private fun tempDir(): File = Files.createTempDirectory("cannelloni-test").toFile()

    private fun withCannelloni(parent: File): File {
        File(parent, "Cannelloni/Config").mkdirs()
        File(parent, "Cannelloni/Config/settings.json").writeText("{}")
        return parent
    }

    @Test fun returnsIndexOfVolumeHoldingExistingCannelloni() {
        val internal = tempDir()
        val sd = withCannelloni(tempDir())
        val volumes = listOf(
            "Internal Storage" to internal.absolutePath + "/",
            "SD card" to sd.absolutePath + "/",
            "Custom" to "",
        )
        assertEquals(1, coordinator.existingCannelloniVolumeIndex(volumes))
    }

    @Test fun prefersRemovableVolumeWhenBothHaveCannelloni() {
        val internal = withCannelloni(tempDir())
        val sd = withCannelloni(tempDir())
        val volumes = listOf(
            "Internal Storage" to internal.absolutePath + "/",
            "SD card" to sd.absolutePath + "/",
            "Custom" to "",
        )
        assertEquals(1, coordinator.existingCannelloniVolumeIndex(volumes))
    }

    @Test fun returnsNullWhenNoExistingCannelloni() {
        val volumes = listOf(
            "Internal Storage" to tempDir().absolutePath + "/",
            "Custom" to "",
        )
        assertNull(coordinator.existingCannelloniVolumeIndex(volumes))
    }

    @Test fun ignoresCustomEntryWithEmptyPath() {
        val volumes = listOf(
            "Internal Storage" to tempDir().absolutePath + "/",
            "Custom" to "",
        )
        assertNull(coordinator.existingCannelloniVolumeIndex(volumes))
    }
}
