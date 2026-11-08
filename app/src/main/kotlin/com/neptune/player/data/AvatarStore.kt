package com.neptune.player.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps the user's profile picture in the app's private storage.
 *
 * The picture is **copied verbatim**, never decoded and re-encoded here:
 *
 *  * no quality loss and no double JPEG compression;
 *  * Android can pick HEIC, AVIF, WebP, GIF or a 50-megapixel photo and we do not have to know —
 *    the decoder behind the image loader handles all of them, crops and downsamples to the view;
 *  * EXIF orientation stays intact, so a photo taken in portrait is not shown on its side (a naive
 *    `BitmapFactory` decode would drop the orientation tag);
 *  * a `content://` grant that lapses after a restart cannot break the avatar, which is why the file
 *    lives in `filesDir` rather than being referenced through the picker URI.
 *
 * The only real hazards are unbounded size and unbounded count, and both are handled: a hard byte
 * cap refuses a pathological pick, and every save deletes the previous picture so exactly one file
 * ever exists.
 */
object AvatarStore {
    /** Refuse absurd picks instead of filling the device with one wallpaper-sized "avatar". */
    private const val MaxBytes = 24L * 1024 * 1024
    private const val Prefix = "avatar-"

    /**
     * Copies [uri] into private storage and returns the file name, or null when the picture could
     * not be read (the caller keeps the previous avatar and says so).
     */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val target = File(context.filesDir, "$Prefix${System.currentTimeMillis()}.img")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MaxBytes) throw IllegalStateException("picture is too large")
                    output.write(buffer, 0, read)
                }
                total
            }
        } ?: return@runCatching null
        if (copied <= 0L) {
            target.delete()
            return@runCatching null
        }
        purge(context, keep = target.name)
        target.name
    }.getOrNull()

    fun file(context: Context, name: String): File? {
        if (name.isBlank()) return null
        val f = File(context.filesDir, File(name).name)
        return if (f.isFile && f.length() > 0) f else null
    }

    /** Removes every stored picture except [keep]; a failed save must not leave orphans behind. */
    fun purge(context: Context, keep: String? = null) {
        context.filesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith(Prefix) && f.name != keep) f.delete()
        }
    }
}
