package com.neptune.player.model

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
        parseId3(bytes) ?: parseVorbis(bytes) ?: parseMp4(bytes)

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
            if (size <= 0 || i + header + size > body.size) break
            val payload = body.copyOfRange(i + header, i + header + size)
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
        if (text.isEmpty()) return null
        val stamped = parseLrc(text)
        val lines = stamped ?: lyricLines(text)
        if (lines.isEmpty()) return null
        return LyricsDoc(lines, LyricsSource.USLT, text)
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
        val text = String(bytes, start + length, end - start - length, Charsets.UTF_8).trim()
        if (text.isEmpty()) return null
        val stamped = parseLrc(text)
        val lines = stamped ?: lyricLines(text)
        if (lines.isEmpty()) return null
        return LyricsDoc(lines, LyricsSource.VORBIS, text)
    }

    // ---- MP4 `©lyr` -----------------------------------------------------------------------------

    private fun parseMp4(bytes: ByteArray): LyricsDoc? {
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val at = indexOf(bytes, marker, bytes.size) ?: return null
        // Atom layout: size(4) name(4) payload. Payload is UTF-8 text, sometimes prefixed by a
        // 2-byte data atom header which we skip when it does not decode as text.
        var i = at + 8
        while (i < bytes.size && (bytes[i] == 0.toByte() || (bytes[i].toInt() and 0xFF) < 0x20)) i += 1
        if (i >= bytes.size) return null
        var end = i
        while (end < bytes.size && bytes[end] != 0.toByte()) end += 1
        val text = String(bytes, i, minOf(end - i, 20_000), Charsets.UTF_8).trim()
        if (text.length < 4 || text.any { it.code in 0x00..0x08 }) return null
        val stamped = parseLrc(text)
        val lines = stamped ?: lyricLines(text)
        if (lines.isEmpty()) return null
        return LyricsDoc(lines, LyricsSource.MP4, text)
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
        if (length <= 0 || offset + length > source.size) return ""
        val slice = source.copyOfRange(offset, offset + length)
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
