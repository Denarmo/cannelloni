package dev.cannelloni.scorza.config

import org.junit.Assert.assertTrue
import org.junit.Test

class CannelloniPathsOfflineTest {
    @Test
    fun offlineDir_isUnderConfigRetroAchievements() {
        val paths = CannelloniPaths("/sd")
        assertTrue(paths.configRaOffline.path.endsWith("/Config/RetroAchievements/Offline"))
    }
}
