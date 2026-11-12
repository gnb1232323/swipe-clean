package com.zakhar.swipeclean

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Миниатюры и превью берём прямо из системы: работает и для фото, и для видео, без сторонних библиотек. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(ctx: Context, m: Media, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${m.id}:${m.isVideo}:$size"
        cache.get(key)
            ?: try {
                ctx.contentResolver.loadThumbnail(m.uri, Size(size, size), null).also { cache.put(key, it) }
            } catch (e: Exception) {
                null
            }
    }
}

@Composable
fun Thumb(media: Media, size: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, media.id, size) {
        value = Thumbs.load(ctx, media, size)?.asImageBitmap()
    }
    Box(modifier.background(Color(0xFF10141A))) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize())
        }
    }
}
