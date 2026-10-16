package dev.cannelloni.scorza.libretro

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Installs the PPSSPP libretro core's system assets (ppge_atlas.zim, flash0 fonts, lang files,
 * shaders, compat.ini, ...) into `<systemDir>/PPSSPP`, where the core looks for them.
 *
 * The release build bundles upstream's `PPSSPP.zip` from the libretro buildbot as the APK asset
 * [ASSET_PATH]. A build without it (local dev builds) still works: [ensureInstalled] then only
 * reports whether the user supplied the folder themselves.
 *
 * Only files that come from the zip are ever written or removed. Anything else in the folder
 * (texture packs, cheats, the core's own SYSTEM/ settings, user-copied fonts) is left alone.
 */
object PpssppAssets {
    private const val TAG = "PpssppAssets"

    /** APK asset path of the bundled zip, populated at build time (see release.yml / taskfile.yml). */
    const val ASSET_PATH = "ppsspp/PPSSPP.zip"

    /** Folder name under the libretro system dir that the PPSSPP core reads. */
    const val DIR_NAME = "PPSSPP"

    /** Marker holding the installed version on its first line and the installed file list after it. */
    const val MARKER_NAME = ".cannelloni-assets"

    /** A file every PPSSPP asset bundle carries; its presence is what "assets present" means. */
    private const val SENTINEL = "ppge_atlas.zim"

    private val lock = Any()

    fun isPpssppCore(coreId: String): Boolean =
        File(coreId).name.lowercase().removePrefix("lib").startsWith("ppsspp")

    /**
     * Makes sure `<systemDir>/PPSSPP` holds the bundled assets, extracting them when missing or
     * when the APK changed since the last install. Does disk IO; call it off the main thread,
     * before loading the core. Returns whether the assets are present afterwards.
     */
    fun ensureInstalled(context: Context, systemDir: File): Boolean {
        synchronized(lock) {
            val version = bundledVersion(context)
            if (version == null) {
                val present = hasAssets(systemDir)
                if (!present) Log.w(TAG, "No bundled $ASSET_PATH and no assets in ${File(systemDir, DIR_NAME)}")
                return present
            }
            if (isUpToDate(systemDir, version)) return true
            return try {
                context.assets.open(ASSET_PATH).use { installFromZip(it, systemDir, version) }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to install PPSSPP assets into $systemDir", e)
                hasAssets(systemDir)
            }
        }
    }

    /** Version string for the bundled zip, or null when this build does not carry one. */
    private fun bundledVersion(context: Context): String? {
        val length = try {
            context.assets.openFd(ASSET_PATH).use { it.length }
        } catch (_: FileNotFoundException) {
            // openFd also throws this for a compressed asset, so confirm it is really absent.
            try {
                context.assets.open(ASSET_PATH).close()
                -1L
            } catch (_: IOException) {
                return null
            }
        } catch (_: IOException) {
            -1L
        }
        val apkStamp = File(context.applicationInfo.sourceDir).lastModified()
        return "$apkStamp:$length"
    }

    fun hasAssets(systemDir: File): Boolean = File(File(systemDir, DIR_NAME), SENTINEL).isFile

    /** True when the marker records [version] and every file it lists is still on disk. */
    fun isUpToDate(systemDir: File, version: String): Boolean {
        val dir = File(systemDir, DIR_NAME)
        val marker = readMarker(dir) ?: return false
        if (marker.first != version) return false
        return marker.second.all { File(dir, it).isFile } && hasAssets(systemDir)
    }

    /**
     * Extracts [zip] into `<systemDir>/PPSSPP`. Entries may be rooted at `PPSSPP/` (upstream's
     * layout, meant to be unpacked into the system dir) or at the asset root; both land in the
     * same place. Files go to a temp dir first and are moved into place only once the whole
     * archive has been read, so a truncated or corrupt zip leaves the existing install untouched.
     * Returns whether the assets are present afterwards.
     */
    fun installFromZip(zip: InputStream, systemDir: File, version: String): Boolean {
        val dir = File(systemDir, DIR_NAME)
        systemDir.mkdirs()
        val tmp = File(systemDir, ".$DIR_NAME.tmp-${System.nanoTime()}")
        try {
            val written = extractTo(zip, tmp)
            if (written.isEmpty()) return hasAssets(systemDir)

            val previous = readMarker(dir)?.second.orEmpty()
            if (!dir.exists()) {
                if (!tmp.renameTo(dir)) moveFiles(tmp, dir, written)
            } else {
                moveFiles(tmp, dir, written)
            }
            // Files a previous bundle installed that this one dropped. User files were never in
            // the list, so they survive.
            val current = written.toSet()
            for (stale in previous) {
                if (stale !in current) safeChild(dir, stale)?.takeIf { it.isFile }?.delete()
            }
            writeMarker(dir, version, written)
            return hasAssets(systemDir)
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }

    /** Unpacks [zip] into [dest], returning the relative paths written (`/`-separated, sorted). */
    internal fun extractTo(zip: InputStream, dest: File): List<String> {
        val written = sortedSetOf<String>()
        dest.mkdirs()
        ZipInputStream(zip.buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val rel = relativeName(entry.name)
                if (rel == null || entry.isDirectory) {
                    zin.closeEntry()
                    continue
                }
                if (rel == MARKER_NAME) throw IOException("Zip carries reserved name $MARKER_NAME")
                val out = safeChild(dest, rel) ?: throw IOException("Unsafe zip entry: ${entry.name}")
                out.parentFile?.mkdirs()
                out.outputStream().use { zin.copyTo(it) }
                written.add(rel)
                zin.closeEntry()
            }
        }
        return written.toList()
    }

    /**
     * Normalizes a zip entry name to a path relative to the PPSSPP folder, or null for an entry
     * that is only the folder itself. Rejects absolute paths and parent traversal.
     */
    internal fun relativeName(entryName: String): String? {
        val name = entryName.replace('\\', '/')
        if (name.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(name)) {
            throw IOException("Absolute zip entry: $entryName")
        }
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw IOException("Zip entry escapes target: $entryName")
        val rel = if (parts.firstOrNull() == DIR_NAME) parts.drop(1) else parts
        return rel.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    /** [rel] resolved under [root], or null if it would land outside it. */
    private fun safeChild(root: File, rel: String): File? {
        val base = root.canonicalFile
        val child = File(base, rel).canonicalFile
        return child.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun moveFiles(from: File, to: File, rels: List<String>) {
        for (rel in rels) {
            val src = File(from, rel)
            val dst = safeChild(to, rel) ?: continue
            dst.parentFile?.mkdirs()
            if (dst.isDirectory) dst.deleteRecursively()
            if (!src.renameTo(dst)) {
                src.copyTo(dst, overwrite = true)
                src.delete()
            }
        }
    }

    private fun readMarker(dir: File): Pair<String, List<String>>? {
        val f = File(dir, MARKER_NAME)
        if (!f.isFile) return null
        val lines = try { f.readLines() } catch (_: IOException) { return null }
        val version = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return version to lines.drop(1).filter { it.isNotBlank() }
    }

    private fun writeMarker(dir: File, version: String, files: List<String>) {
        val f = File(dir, MARKER_NAME)
        val tmp = File(dir, "$MARKER_NAME.tmp")
        tmp.writeText((listOf(version) + files).joinToString("\n", postfix = "\n"))
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }
}
