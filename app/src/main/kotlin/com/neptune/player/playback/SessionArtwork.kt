package com.neptune.player.playback

import android.content.Context
import com.neptune.player.R

/**
 * Artwork for the media session — the surface the lock screen, the notification, Android Auto and
 * "dynamic island" style overlays read.
 *
 * `BitmapLoader.loadBitmapFromMetadata` checks `artworkData` first and only then `artworkUri`. A
 * track with no embedded cover used to send *neither*, so those surfaces had nothing to draw and
 * substituted the playback-state glyph — the pause icon the brief complained about. Attaching the
 * app mark as real bytes means every track carries artwork, the way streaming apps do it.
 *
 * The PNG is baked by `ci/make_media_art.py` from the same `orbit` glyph as the launcher, so the
 * mark is identical to the app icon. Reading a resource avoids allocating a Bitmap and running a
 * Canvas on the main thread for every queue entry; the bytes are read once and cached. At ~9 KB the
 * payload stays cheap even in a long queue, which Media3 transfers in IPC-sized chunks anyway.
 */
object SessionArtwork {
    @Volatile private var cached: ByteArray? = null

    /** PNG bytes of the app mark, or null if the resource could not be read. */
    fun brandPng(context: Context): ByteArray? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                context.resources.openRawResource(R.raw.ne_media_art).use { it.readBytes() }
            }.getOrNull()?.also { cached = it }
        }
    }
}
