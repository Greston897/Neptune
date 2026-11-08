package com.neptune.player

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test-only audio, generated on the device.
 *
 * 2.9 removed the bundled demo catalogue, so the app ships no music and a fresh install has an empty
 * library. Playback still has to be proven end to end, so the suite writes its own WAV into the
 * cache and pushes it through the real import path (`LibraryRepository.importUris`) — the same code
 * a user reaches with the document picker. Nothing is added to the shipped APK.
 *
 * A single file is seeded on purpose: track titles come from metadata, a bare WAV has none, so two
 * files would both be "Без названия" and every `Действия с <title>` node would be ambiguous.
 */
object AudioFixture {
    private const val SAMPLE_RATE = 22050
    private const val SECONDS = 12

    /** Writes a valid mono 16-bit PCM WAV and returns its `file://` URI. */
    fun write(context: Context): Uri {
        val file = File(context.cacheDir, "fixture-tone.wav")
        val frames = SAMPLE_RATE * SECONDS
        val dataBytes = frames * 2
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
            fun le32(value: Int) {
                out.write(value and 0xFF); out.write((value ushr 8) and 0xFF)
                out.write((value ushr 16) and 0xFF); out.write((value ushr 24) and 0xFF)
            }
            fun le16(value: Int) { out.write(value and 0xFF); out.write((value ushr 8) and 0xFF) }

            ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
            ascii("fmt "); le32(16); le16(1); le16(1)
            le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2); le16(2); le16(16)
            ascii("data"); le32(dataBytes)

            // A quiet two-note tone: real signal, so the decoder reports advancing positions rather
            // than treating the file as silence and finishing immediately.
            val buffer = ByteArray(8192)
            var written = 0
            while (written < frames) {
                val chunk = minOf(frames - written, buffer.size / 2)
                for (i in 0 until chunk) {
                    val t = (written + i).toDouble() / SAMPLE_RATE
                    val wave = 0.22 * sin(2 * PI * 220.0 * t) + 0.14 * sin(2 * PI * 330.0 * t)
                    val sample = (wave * Short.MAX_VALUE).toInt().toShort()
                    buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                    buffer[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
                }
                out.write(buffer, 0, chunk * 2)
                written += chunk
            }
        }
        return Uri.fromFile(file)
    }
}
