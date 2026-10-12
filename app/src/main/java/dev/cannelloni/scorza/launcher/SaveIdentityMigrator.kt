package dev.cannelloni.scorza.launcher

import dev.cannelloni.scorza.config.CannelloniPaths
import dev.cannelloni.scorza.libretro.SaveSlotManager
import dev.cannelloni.scorza.util.ArchiveExtractor
import dev.cannelloni.scorza.util.AtomicRename
import java.io.File
import java.text.Normalizer

// Heals pre-fix save data for archived games whose on-disk identity is the
// archive's inner entry name rather than the library (zip) name. Lazy: runs at
// launch, short-circuits once library-named data exists. Sunset once the early
// user base has rolled forward - delete this class and its launchEmbedded call.
class SaveIdentityMigrator(cannelloniRoot: File, private val atomicRename: AtomicRename) {

    private val paths = CannelloniPaths(cannelloniRoot)

    fun migrateOnLaunch(platformTag: String, libraryBaseName: String, archive: File) {
        if (libraryDataExists(platformTag, libraryBaseName)) return

        val innerRaw = ArchiveExtractor.primaryEntryName(archive) ?: return
        val innerBase = Normalizer.normalize(File(innerRaw).nameWithoutExtension, Normalizer.Form.NFC)
        if (innerBase == libraryBaseName) return

        atomicRename.relocateSaveData(platformTag, innerBase, libraryBaseName)
    }

    private fun libraryDataExists(tag: String, base: String): Boolean {
        val slots = SaveSlotManager(paths.saveStateBase(tag, base).absolutePath)
        if (slots.slots.any { slots.stateExists(it) }) return true
        return paths.sramFile(tag, base).exists()
    }
}
