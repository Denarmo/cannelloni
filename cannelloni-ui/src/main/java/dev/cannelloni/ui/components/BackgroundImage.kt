package dev.cannelloni.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.cannelloni.ui.theme.LocalCannelloniColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class BgCacheEntry(val path: String, val lastModified: Long, val bitmap: ImageBitmap)
private val cache = java.util.concurrent.atomic.AtomicReference<BgCacheEntry?>(null)

@Composable
fun ScreenBackground(
    backgroundImagePath: String?,
    backgroundTint: Int = 0,
    backgroundAlpha: Float = 1f,
    backgroundColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val resolvedColor = backgroundColor ?: LocalCannelloniColors.current.background
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(resolvedColor.copy(alpha = backgroundAlpha)))

        if (backgroundImagePath != null) {
            val lastModified = File(backgroundImagePath).lastModified()
            val cachedInitial = cache.get()?.takeIf {
                it.path == backgroundImagePath && it.lastModified == lastModified
            }?.bitmap
            val bitmap by produceState(
                initialValue = cachedInitial,
                backgroundImagePath, lastModified
            ) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val file = File(backgroundImagePath)
                        if (!file.exists()) return@withContext null
                        val mtime = file.lastModified()
                        val existing = cache.get()
                        if (existing != null && existing.path == backgroundImagePath && existing.lastModified == mtime) {
                            return@withContext existing.bitmap
                        }
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.also {
                            cache.set(BgCacheEntry(backgroundImagePath, mtime, it))
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (backgroundTint > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = backgroundTint / 100f))
                    )
                }
            }
        }

        content()
    }
}
