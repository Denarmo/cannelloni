package dev.cannelloni.scorza.romm.art

import dev.cannelloni.scorza.di.CannelloniPathsProvider
import dev.cannelloni.scorza.romm.RommArtUrl
import dev.cannelloni.scorza.romm.RommHttp
import dev.cannelloni.scorza.util.ScanLog
import okhttp3.Request
import java.io.File

class RommArtDownloader(
    private val http: RommHttp,
    private val paths: CannelloniPathsProvider,
) {
    /** Downloads [coverPath] for [tag]/[baseName] into the Art dir. Returns true on success. */
    fun download(host: String, coverPath: String?, tag: String, baseName: String): Boolean {
        val url = RommArtUrl.resolve(host, coverPath) ?: return false
        return try {
            http.client().newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val ext = run {
                    val segment = url.substringBefore('?').substringAfterLast('/')
                    val candidate = segment.substringAfterLast('.', "")
                    if (candidate.length in 2..5 && candidate.all { it.isLetterOrDigit() }) candidate else "png"
                }
                val artDir = File(paths.root, "Art/$tag").apply { mkdirs() }
                val dest = File(artDir, "$baseName.$ext")
                val temp = File(artDir, "$baseName.$ext.part")
                try {
                    temp.outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
                    if (temp.length() == 0L) { temp.delete(); return false }
                    if (dest.exists()) dest.delete()
                    if (!temp.renameTo(dest)) { temp.copyTo(dest, overwrite = true); temp.delete() }
                } catch (e: Exception) {
                    temp.delete()
                    throw e
                }
                true
            }
        } catch (e: Exception) {
            ScanLog.write("romm art download failed for $tag/$baseName: ${e.message}")
            false
        }
    }
}
