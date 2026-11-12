package com.zakhar.swipeclean

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Media(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val taken: Long,
    val size: Long,
    val mime: String,
    val bucket: String,
    val durationMs: Long,
    val favorite: Boolean,
) {
    val isScreenshot: Boolean
        get() = bucket.contains("screenshot", ignoreCase = true) || name.startsWith("Screenshot", ignoreCase = true)
}

/** Что решил человек про файл. */
enum class Decision(val code: Char) {
    KEEP('K'),
    LIKE('L'),
    TRASH('T'),
    FAVORITED('F'); // лайк, уже отмеченный избранным в системной галерее

    companion object {
        fun from(c: Char): Decision? = entries.firstOrNull { it.code == c }
    }
}

enum class Filter(val label: String) {
    ALL("Всё"),
    PHOTOS("Фото"),
    VIDEOS("Видео"),
    SHOTS("Скриншоты");

    fun matches(m: Media): Boolean = when (this) {
        ALL -> true
        PHOTOS -> !m.isVideo
        VIDEOS -> m.isVideo
        SHOTS -> m.isScreenshot
    }
}

object MediaRepo {
    /** Все фото и видео устройства, сначала новые. */
    fun load(cr: ContentResolver): List<Media> {
        val files = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val out = ArrayList<Media>()
        cr.query(files, projection, selection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val takenRaw = c.getLong(iTaken)
                out.add(
                    Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        isVideo = video,
                        name = c.getString(iName) ?: "",
                        taken = if (takenRaw > 0) takenRaw else c.getLong(iAdded) * 1000,
                        size = c.getLong(iSize),
                        mime = c.getString(iMime) ?: "",
                        bucket = c.getString(iBucket) ?: "",
                        durationMs = c.getLong(iDur),
                        favorite = c.getInt(iFav) == 1,
                    )
                )
            }
        }
        out.sortByDescending { it.taken }
        return out
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale("ru"), "%.1f ГБ", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format(java.util.Locale("ru"), "%.0f МБ", bytes / (1024.0 * 1024))
    else -> String.format(java.util.Locale("ru"), "%.0f КБ", bytes / 1024.0)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale("ru")).format(java.util.Date(ms))

fun Uri.toDebug(): String = toString()
