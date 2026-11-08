package com.neptune.player.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * One lyric line and when it should light up.
 *
 * `timeMs` is absolute from the start of the track. A line read from a plain unsynchronised tag has
 * `timeMs = -1`, which [LyricsDoc.synced] uses to switch the whole view out of karaoke mode: showing
 * invented timings would be worse than showing none.
 */
data class LyricLine(val timeMs: Long, val text: String)

/** Where a text came from. Surfaced in the UI so the user knows what to trust. */
enum class LyricsSource(val title: String) {
    SYLT("Синхронизированный тег"),
    USLT("Тег файла"),
    VORBIS("Vorbis-тег"),
    MP4("MP4-тег"),
    LRC("LRC"),
    SAVED("Мой текст"),
    NONE("Не найден"),
}

/**
 * A track's lyrics plus everything needed to render karaoke against real time.
 *
 * Kept immutable and position-free: the active line is a pure function of `positionMs`, so a frame
 * costs a binary search and nothing recomposes the parser.
 */
data class LyricsDoc(
    val lines: List<LyricLine>,
    val source: LyricsSource,
    val raw: String = "",
) {
    val isEmpty: Boolean get() = lines.isEmpty() || lines.all { it.text.isBlank() }

    /**
     * A text is synchronised when **every** line carries a real time, there are at least two of
     * them and the times grow.
     *
     * All-or-nothing is deliberate on two counts: one timestamped line is not a karaoke track, it is
     * a typo, and a text where only some lines are stamped cannot be binary-searched (the -1 entries
     * would break the ordering), so the highlight would silently land on the wrong line.
     */
    val synced: Boolean
        get() = lines.size >= 2 && lines.all { it.timeMs >= 0 } &&
            lines.zipWithNext().all { (a, b) -> b.timeMs >= a.timeMs }

    /**
     * Index of the line that should be highlighted at [positionMs], or -1 before the first one.
     *
     * For an unsynchronised text every line is always "readable", so the caller scrolls by hand;
     * this returns -1 and the UI simply highlights nothing.
     */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs in 0..positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    /** 0..1 progress inside the active line, used for the moving fill behind the karaoke text. */
    fun lineProgress(positionMs: Long): Float {
        val index = activeIndex(positionMs)
        if (index < 0) return 0f
        val start = lines[index].timeMs
        val end = lines.getOrNull(index + 1)?.timeMs?.takeIf { it > start } ?: (start + 4000)
        return ((positionMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    companion object {
        val None = LyricsDoc(emptyList(), LyricsSource.NONE)
    }
}

/**
 * Text extraction that runs on the JVM without Android classes.
 *
 * Sources, in the order the repository tries them:
 *
 *  1. **ID3v2 SYLT** — synchronised lyrics: a real timestamp per line, which is what karaoke needs.
 *  2. **ID3v2 USLT** — plain lyrics in MP3 tags, the most common case.
 *  3. **Vorbis `LYRICS`/`UNSYNCEDLYRICS`** — FLAC and Ogg.
 *  4. **MP4 `©lyr`** — m4a/aac.
 *  5. **LRC** — a text the user pasted or saved, with `[mm:ss.xx]` stamps.
 *
 * Everything is defensive: a truncated tag, an unknown encoding or a random 0x00 must produce
 * `null`, never an exception on the playback thread.
 */
object LyricsParser {

    /** Hard cap on what we are willing to read from a file before assuming there are no lyrics. */
    const val MaxScanBytes = 4 * 1024 * 1024

    private const val EncIso = 0
    private const val EncUtf16Bom = 1
    private const val EncUtf16Be = 2
    private const val EncUtf8 = 3

    /**
     * Parses whatever was found in the file: ID3 first, then Vorbis, then MP4, then LRC.
     * Returns null when nothing usable is present.
     */
    fun fromBytes(bytes: ByteArray): LyricsDoc? =
        parseId3(bytes) ?: parseVorbis(bytes) ?: parseMp4(bytes) ?: parseSidecar(bytes)

    /**
     * Last resort for a plain `.lrc`/`.txt` file: the payload itself is the text.
     *
     * A tag that names lyrics but carries unreadable or empty text is not a reason to give up —
     * the file may well have a perfectly good text next to it, so every other source is tried
     * before this one.
     */
    /** Legacy Russian `.lrc` files are routinely saved in cp1251; UTF-8 is tried first. */
    private val windows1251: Charset = Charset.forName("windows-1251")

    private fun parseSidecar(bytes: ByteArray): LyricsDoc? {
        if (bytes.isEmpty() || bytes.size > MaxScanBytes) return null
        val text = decodeLoose(bytes) ?: return null
        return lyricDoc(text.trim(), LyricsSource.LRC)
    }

    /**
     * Decodes a whole file that might be a text file, and returns null when it is not text at all.
     *
     * Strict UTF-8 first; a Russian `.lrc` saved in windows-1251 is common enough that a failed
     * strict decode falls back to it rather than dropping the file. The printability check is what
     * keeps a JPEG or a truncated frame from being printed as mojibake: binary content has NULs and
     * runs of control characters, text does not.
     */
    private fun decodeLoose(bytes: ByteArray): String? {
        val zeroFree = decodeStrict(bytes, Charsets.UTF_8) ?: decodeStrict(bytes, windows1251)
        val decoded = if (zeroFree != null) zeroFree else decodeUtf16(bytes) ?: return null
        val clean = decoded.trim().trimStart('\uFEFF')
        if (clean.isEmpty()) return null
        val controls = clean.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        if (controls * 20 > clean.length) return null
        return clean
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Single-byte and UTF-8 text never decodes as UTF-16, so trying both is safe and ordered. */
    private fun decodeUtf16(bytes: ByteArray): String? {
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            looksLikeUtf16(bytes) -> Charsets.UTF_16LE
            else -> return null
        }
        return decodeStrict(bytes, charset)
    }

    /** A UTF-16 text file is full of NULs but is still text; it must not be rejected as binary. */
    private fun looksLikeUtf16(bytes: ByteArray): Boolean {
        val bom = bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
            (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        if (bom) return true
        // Without a BOM: every other byte being zero is the signature of Latin/Cyrillic UTF-16.
        val head = bytes.take(64)
        val even = head.filterIndexed { i, _ -> i % 2 == 0 }.count { it == 0.toByte() }
        val odd = head.filterIndexed { i, _ -> i % 2 == 1 }.count { it == 0.toByte() }
        return even > head.size / 4 || odd > head.size / 4
    }

    /**
     * Any text the user can see, parsed as LRC when it carries `[mm:ss]` stamps and as plain text
     * otherwise. This is also the path a saved lyric takes.
     */
    fun fromText(text: String, source: LyricsSource = LyricsSource.LRC): LyricsDoc? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val stamped = parseLrc(clean)
        val lines = stamped ?: lyricLines(clean)
        if (lines.isEmpty()) return null
        return LyricsDoc(lines, source, clean)
    }

    // ---- LRC -----------------------------------------------------------------------------------

    private val lrcStamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** ID3-style non-lyric tags (`[ar:…]`, `[ti:…]`, `[offset:…]`) that must never be printed. */
    private val metaTag = Regex("""^\[[a-zA-Z#]+:.*]$""")

    /** Strips metadata tags from an unstamped text and keeps the lines that are actually lyrics. */
    private fun lyricLines(text: String): List<LyricLine> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !metaTag.matches(it) }
        .map { LyricLine(-1L, it) }
        .toList()

    /**
     * Parses LRC. Several stamps on one line are honoured (`[00:12.00][01:20.00]chorus`), because
     * that is exactly how LRC files encode a repeated chorus and dropping the extra stamps would
     * make the highlight jump backwards.
     */
    fun parseLrc(text: String): List<LyricLine>? {
        val out = mutableListOf<LyricLine>()
        var sawStamp = false
        text.lineSequence().forEach { line ->
            val stamps = lrcStamp.findAll(line).toList()
            if (stamps.isNotEmpty()) {
                val body = line.substring(stamps.last().range.last + 1).trim()
                // Each stamp is parsed on its own: a malformed pair skips that stamp only, and the
                // whole block is a single loop so no `return@forEach` can bind to the wrong lambda.
                for (stamp in stamps) {
                    val minutes = stamp.groupValues[1].toLongOrNull()
                    val seconds = stamp.groupValues[2].toLongOrNull()
                    if (minutes == null || seconds == null) continue
                    sawStamp = true
                    val fractionRaw = stamp.groupValues[3]
                    val fractionMs = when (fractionRaw.length) {
                        0 -> 0L
                        1 -> fractionRaw.toLong() * 100
                        2 -> fractionRaw.toLong() * 10
                        else -> fractionRaw.take(3).toLong()
                    }
                    val time = minutes * 60_000 + seconds * 1000 + fractionMs
                    if (body.isNotEmpty()) out += LyricLine(time, body)
                }
            }
        }
        if (!sawStamp) return null
        return out.sortedBy { it.timeMs }
    }

    // ---- ID3v2 ---------------------------------------------------------------------------------

    private class Frame(val id: String, val payload: ByteArray)

    /** Reads the ID3v2 tag at the head of the file, if any. */
    fun parseId3(bytes: ByteArray): LyricsDoc? {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return null
        val major = bytes[3].toInt() and 0xFF
        if (major !in 2..4) return null
        val flags = bytes[5].toInt() and 0xFF
        val declared = synchsafe(bytes, 6, 4) ?: return null
        val end = minOf(10 + declared, bytes.size)
        var body = bytes.copyOfRange(10, end)
        // Tag-level unsynchronisation replaces every FF 00 pair with FF. Undo it before parsing,
        // otherwise frame sizes point at the wrong bytes.
        if (flags and 0x80 != 0) body = deUnsynchronise(body)
        val header = if (major == 2) 6 else 10
        val idLength = if (major == 2) 3 else 4
        val collected = mutableListOf<Frame>()
        var i = 0
        while (i + header <= body.size) {
            val id = String(body, i, idLength, Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val size = if (major == 2) {
                ((body[i + 3].toInt() and 0xFF) shl 16) or ((body[i + 4].toInt() and 0xFF) shl 8) or (body[i + 5].toInt() and 0xFF)
            } else if (major == 4) {
                synchsafe(body, i + 4, 4) ?: break
            } else {
                ((body[i + 4].toInt() and 0xFF) shl 24) or ((body[i + 5].toInt() and 0xFF) shl 16) or
                    ((body[i + 6].toInt() and 0xFF) shl 8) or (body[i + 7].toInt() and 0xFF)
            }
            // Long arithmetic on purpose: `i + header + size` overflows to a *negative* number for a
            // corrupt 0x7FFFFFFF size, the bound check then passes, and `copyOfRange` is asked for a
            // 2 GB array — an OutOfMemoryError from eight bytes of input.
            val frameEnd = i.toLong() + header + size
            if (size <= 0 || frameEnd > body.size) break
            val payload = body.copyOfRange(i + header, frameEnd.toInt())
            val formatFlags = if (major == 2) 0 else body[i + 9].toInt() and 0xFF
            collected += Frame(id, stripFrameFormat(major, formatFlags, payload))
            i += header + size
        }
        // SYLT wins: it is the only source with real timing.
        collected.firstOrNull { it.id == "SYLT" || it.id == "SLT" }?.let { frame ->
            parseSylt(frame.payload)?.let { doc -> if (!doc.isEmpty) return doc }
        }
        collected.firstOrNull { it.id == "USLT" || it.id == "ULT" }?.let { frame ->
            parseUslt(frame.payload)?.let { doc -> if (!doc.isEmpty) return doc }
        }
        return null
    }

    /**
     * Data-length indicator and unsynchronisation are the two frame-level transformations that
     * change the payload; both are stripped so the parsers below always see the raw text.
     */
    private fun stripFrameFormat(major: Int, flags: Int, payload: ByteArray): ByteArray {
        if (major < 3 || flags == 0) return payload
        var start = 0
        var end = payload.size
        // v2.3: compression(0x80), encryption(0x40), grouping(0x20); v2.4: grouping(0x40),
        // compression(0x08), encryption(0x04), unsynchronisation(0x02), data-length(0x01).
        val grouping = if (major == 4) 0x40 else 0x20
        val dataLength = major == 4 && flags and 0x01 != 0
        if (flags and grouping != 0) start += 1
        if (dataLength) end -= 4
        if (start >= end) return ByteArray(0)
        return payload.copyOfRange(start, end)
    }

    private fun deUnsynchronise(input: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(input.size)
        var i = 0
        while (i < input.size) {
            out.write(input[i].toInt())
            if (input[i] == 0xFF.toByte() && i + 1 < input.size && input[i + 1] == 0.toByte()) i += 2 else i += 1
        }
        return out.toByteArray()
    }

    private fun parseUslt(payload: ByteArray): LyricsDoc? {
        if (payload.size < 5) return null
        val encoding = payload[0].toInt() and 0xFF
        // encoding(1) + language(3) + descriptor(NUL-terminated) + text
        var i = 4
        val descriptorEnd = findTerminator(payload, i, encoding)
        i = if (descriptorEnd < 0) i else descriptorEnd + terminatorLength(encoding)
        if (i >= payload.size) return null
        val text = decode(payload, i, payload.size - i, encoding).trim()
        return lyricDoc(text, LyricsSource.USLT)
    }

    /**
     * SYLT: `encoding(1) language(3) timestampFormat(1) contentType(1) descriptor…`
     * followed by repeated `text NUL time(4)` pairs. Timestamp format 1 is MPEG frames, which we
     * cannot convert without the audio's frame duration — those lines are dropped rather than
     * faked, and the file falls back to USLT.
     */
    private fun parseSylt(payload: ByteArray): LyricsDoc? {
        if (payload.size < 7) return null
        val encoding = payload[0].toInt() and 0xFF
        val format = payload[4].toInt() and 0xFF
        if (format != 2) return null
        var i = 6
        val descriptorEnd = findTerminator(payload, i, encoding)
        i = if (descriptorEnd < 0) i else descriptorEnd + terminatorLength(encoding)
        val lines = mutableListOf<LyricLine>()
        while (i < payload.size) {
            val end = findTerminator(payload, i, encoding)
            if (end < 0 || end + terminatorLength(encoding) + 4 > payload.size) break
            val text = decode(payload, i, end - i, encoding).trim()
            val at = end + terminatorLength(encoding)
            val time = ((payload[at].toLong() and 0xFF) shl 24) or ((payload[at + 1].toLong() and 0xFF) shl 16) or
                ((payload[at + 2].toLong() and 0xFF) shl 8) or (payload[at + 3].toLong() and 0xFF)
            if (text.isNotEmpty()) lines += LyricLine(time, text)
            i = at + 4
        }
        if (lines.isEmpty()) return null
        return LyricsDoc(lines.sortedBy { it.timeMs }, LyricsSource.SYLT, lines.joinToString("\n") { it.text })
    }

    // ---- Vorbis comment (FLAC / Ogg) -----------------------------------------------------------

    private fun parseVorbis(bytes: ByteArray): LyricsDoc? {
        val marker = "LYRICS=".toByteArray(Charsets.ISO_8859_1)
        val alt = "UNSYNCEDLYRICS=".toByteArray(Charsets.ISO_8859_1)
        val hit = indexOf(bytes, marker, bytes.size)?.let { it to marker.size }
            ?: indexOf(bytes, alt, bytes.size)?.let { it to alt.size } ?: return null
        val (start, length) = hit
        var end = start + length
        while (end < bytes.size && bytes[end] != 0.toByte()) end += 1
        if (end <= start + length) return null
        return readText(bytes, start + length, end - start - length, Charsets.UTF_8, LyricsSource.VORBIS)
    }

    // ---- MP4 `©lyr` -----------------------------------------------------------------------------

    private fun parseMp4(bytes: ByteArray): LyricsDoc? {
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val at = indexOf(bytes, marker, bytes.size) ?: return null
        // `©lyr` is a container: size(4) name(4) then a `data` atom holding version/flags(4) and the
        // text. Reading the raw bytes after the name would pick up the `data` header itself, which is
        // why this walks the child atom rather than skipping control characters.
        val containerStart = at - 4
        val containerEnd = if (containerStart >= 0) {
            val size = readInt32(bytes, containerStart) ?: return null
            (containerStart.toLong() + size).coerceAtMost(bytes.size.toLong()).toInt().coerceAtLeast(at + 4)
        } else bytes.size
        var i = at + 4
        while (i + 8 <= containerEnd) {
            val size = readInt32(bytes, i) ?: break
            // Same overflow trap as the ID3 frames: a Long bound makes a corrupt size just stop.
            val atomEnd = i.toLong() + size
            if (size < 8 || atomEnd > bytes.size) break
            val name = String(bytes, i + 4, 4, Charsets.ISO_8859_1)
            if (name == "data") {
                // 4 bytes version/flags, then the payload of this atom.
                val textStart = i + 12
                val textEnd = atomEnd.toInt()
                if (textStart >= textEnd) return null
                return readText(bytes, textStart, textEnd - textStart, Charsets.UTF_8, LyricsSource.MP4)
            }
            i = atomEnd.toInt()
        }
        return null
    }

    private fun readText(source: ByteArray, offset: Int, length: Int, charset: Charset, kind: LyricsSource): LyricsDoc? =
        lyricDoc(String(source, offset, length, charset).trim().trimStart('\uFEFF'), kind)

    /** Shared tail of every text container: LRC when stamps are present, plain lines otherwise. */
    private fun lyricDoc(text: String, source: LyricsSource): LyricsDoc? {
        if (text.isEmpty()) return null
        val lines = parseLrc(text) ?: lyricLines(text)
        if (lines.isEmpty()) return null
        return LyricsDoc(lines, source, text)
    }

    private fun readInt32(source: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > source.size) return null
        return ((source[offset].toInt() and 0xFF) shl 24) or ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or (source[offset + 3].toInt() and 0xFF)
    }

    // ---- primitives ----------------------------------------------------------------------------

    /** 28-bit synchsafe integer: every byte carries seven significant bits. */
    private fun synchsafe(source: ByteArray, offset: Int, length: Int): Int? {
        if (offset + length > source.size) return null
        var value = 0
        for (k in 0 until length) {
            val b = source[offset + k].toInt() and 0xFF
            if (b and 0x80 != 0) return null
            value = (value shl 7) or b
        }
        return if (value < 0) null else value
    }

    private fun terminatorLength(encoding: Int) = if (encoding == EncUtf16Bom || encoding == EncUtf16Be) 2 else 1

    private fun findTerminator(source: ByteArray, from: Int, encoding: Int): Int {
        val wide = terminatorLength(encoding) == 2
        var i = from
        while (i + (if (wide) 1 else 0) < source.size) {
            if (wide) {
                if (source[i] == 0.toByte() && source[i + 1] == 0.toByte()) return i
                i += 2
            } else {
                if (source[i] == 0.toByte()) return i
                i += 1
            }
        }
        return -1
    }

    private fun decode(source: ByteArray, offset: Int, length: Int, encoding: Int): String {
        val end = offset.toLong() + length
        if (length <= 0 || offset < 0 || end > source.size) return ""
        val slice = source.copyOfRange(offset, end.toInt())
        return runCatching {
            when (encoding) {
                EncUtf16Bom -> String(slice, Charsets.UTF_16)
                EncUtf16Be -> String(slice, Charsets.UTF_16BE)
                EncUtf8 -> String(slice, Charsets.UTF_8)
                else -> String(slice, Charsets.ISO_8859_1)
            }
        }.getOrDefault("").replace("\u0000", "").trim()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, limit: Int): Int? {
        if (needle.isEmpty()) return null
        val last = minOf(limit, haystack.size) - needle.size
        for (i in 0..last) {
            var hit = true
            for (k in needle.indices) {
                if (haystack[i + k] != needle[k]) { hit = false; break }
            }
            if (hit) return i
        }
        return null
    }
}
