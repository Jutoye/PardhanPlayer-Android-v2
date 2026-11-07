package com.pardhancodes.pardhanplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class MediaEntry(
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val path: String,
    val size: Long
) {
    val folder: String get() = path.substringBeforeLast('/', "")
    fun toItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}

object MediaScanner {
    fun scan(ctx: Context, video: Boolean): List<MediaEntry> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val durCol = if (video) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            durCol,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )
        val out = ArrayList<MediaEntry>()
        runCatching {
            ctx.contentResolver.query(
                collection, proj, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dI = c.getColumnIndexOrThrow(durCol)
                val pI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    out.add(
                        MediaEntry(
                            ContentUris.withAppendedId(collection, c.getLong(idI)),
                            c.getString(nI) ?: "Unknown",
                            c.getLong(dI),
                            video,
                            c.getString(pI) ?: "",
                            c.getLong(sI)
                        )
                    )
                }
            }
        }
        return out
    }
}
