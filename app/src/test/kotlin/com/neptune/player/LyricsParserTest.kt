package com.neptune.player

import com.neptune.player.model.LyricLine
import com.neptune.player.model.LyricsDoc
import com.neptune.player.model.LyricsParser
import com.neptune.player.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lyrics extractor is the riskiest new code in 3.0: it reads binary tags written by other
 * programs, so every branch here corresponds to a real-world file shape (iTunes m4a, an old MP3 with
 * ID3v2.3, a FLAC with Vorbis comments, a hand-made LRC).
 */
class LyricsParserTest {

    // ---- LRC -----------------------------------------------------------------------------------

    @Test fun lrcTimestampsBecomeMilliseconds() {
        val doc = LyricsParser.fromText("[00:12.34]Первая строка\n[01:02.5]Вторая")!!
        assertEquals(12_340L, doc.lines[0].timeMs)
        assertEquals(62_500L, doc.lines[1].timeMs)
        assertEquals("Вторая", doc.lines[1].text)
        assertTrue("two ordered stamps are a karaoke text", doc.synced)
    }

    @Test fun lrcAcceptsColonFractionsAndThreeDigitMillis() {
        val doc = LyricsParser.fromText("[00:05:25]a\n[00:07.125]b")!!
        assertEquals(5_250L, doc.lines[0].timeMs)
        assertEquals(7_125L, doc.lines[1].timeMs)
    }

    @Test fun repeatedChorusStampsAreAllKept() {
        // `[00:10][01:10]припев` is how LRC encodes a chorus; dropping the second stamp would make
        // the highlight jump back and stay there.
        val doc = LyricsParser.fromText("[00:10.00][01:10.00]припев\n[00:20.00]куплет")!!
        assertEquals(listOf(10_000L, 20_000L, 70_000L), doc.lines.map { it.timeMs })
    }

    @Test fun unsynchronisedTextIsNotMarkedAsKaraoke() {
        val doc = LyricsParser.fromText("просто\nтекст\nбез таймингов")!!
        assertFalse("invented timings would be worse than none", doc.synced)
        assertTrue(doc.lines.all { it.timeMs < 0 })
        assertEquals(-1, doc.activeIndex(30_000))
    }

    @Test fun blankTextIsRejectedInsteadOfStored() {
        assertNull(LyricsParser.fromText("   \n\n  "))
        assertNull(LyricsParser.fromText(""))
    }

    // ---- active line maths ---------------------------------------------------------------------

    private val doc = LyricsDoc(
        listOf(
            LyricLine(0, "раз"),
            LyricLine(5_000, "два"),
            LyricLine(9_000, "три"),
            LyricLine(20_000, "четыре"),
        ),
        LyricsSource.LRC,
    )

    @Test fun activeLineFollowsPositionExactly() {
        assertEquals(0, doc.activeIndex(0))
        assertEquals(0, doc.activeIndex(4_999))
        assertEquals(1, doc.activeIndex(5_000))
        assertEquals(2, doc.activeIndex(19_999))
        assertEquals(3, doc.activeIndex(86_000))
    }

    @Test fun beforeTheFirstLineNothingIsHighlighted() {
        assertTrue(doc.copy(lines = listOf(LyricLine(3_000, "позже"))).synced.not())
        assertEquals(-1, LyricsDoc(listOf(LyricLine(3_000, "позже"), LyricLine(8_000, "ещё")), LyricsSource.LRC)
            .activeIndex(1_000))
    }

    @Test fun lineProgressIsBoundedAndMonotonic() {
        assertEquals(0f, doc.lineProgress(5_000), 0.001f)
        val half = doc.lineProgress(7_000)
        assertTrue("progress inside the line: $half", half > 0.45f && half < 0.55f)
        assertEquals(1f, doc.lineProgress(8_999), 0.01f)
        // A last line has no successor: the fallback window must still produce a sane value.
        assertTrue(doc.lineProgress(60_000) in 0f..1f)
    }

    @Test fun outOfOrderStampsFallBackToUnsynchronised() {
        val broken = LyricsDoc(listOf(LyricLine(5_000, "a"), LyricLine(1_000, "b")), LyricsSource.LRC)
        assertFalse(broken.synced)
        assertEquals(-1, broken.activeIndex(6_000))
    }

    // ---- ID3 -----------------------------------------------------------------------------------

    /** Minimal ID3v2.3 tag with one USLT frame holding UTF-8 lyrics. */
    private fun id3v23Uslt(text: String): ByteArray {
        val payload = byteArrayOf(3) + "rus".toByteArray() + byteArrayOf(0) + byteArrayOf(0) +
            text.toByteArray(Charsets.UTF_8)
        val frame = "USLT".toByteArray() + int32(payload.size) + byteArrayOf(0, 0) + payload
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + synchsafe(frame.size) + frame
    }

    private fun int32(value: Int) = byteArrayOf(
        (value shr 24 and 0xFF).toByte(), (value shr 16 and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(), (value and 0xFF).toByte(),
    )

    private fun synchsafe(value: Int) = byteArrayOf(
        (value shr 21 and 0x7F).toByte(), (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(), (value and 0x7F).toByte(),
    )

    @Test fun id3UsltFrameIsRead() {
        val doc = LyricsParser.fromBytes(id3v23Uslt("[00:01.00]привет\n[00:02.00]мир"))
        assertNotNull("USLT must be picked up", doc)
        assertEquals(LyricsSource.USLT, doc!!.source)
        assertEquals("привет", doc.lines.first().text)
        assertTrue("stamped USLT text still becomes karaoke", doc.synced)
    }

    @Test fun truncatedOrForeignBytesProduceNullInsteadOfThrowing() {
        listOf(
            ByteArray(0),
            byteArrayOf(1, 2, 3, 4),
            "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F),
            "ID3".toByteArray() + byteArrayOf(9, 0, 0, 0, 0, 0, 0) + ByteArray(32),
            id3v23Uslt("").copyOfRange(0, 20),
        ).forEach { assertNull("must not throw on ${it.size} bytes", runCatching { LyricsParser.fromBytes(it) }.getOrNull()) }
    }

    @Test fun frameSizeLargerThanTagIsIgnoredNotOverRead() {
        // A corrupt size field used to be the classic way to make a reader run off the buffer.
        val payload = "x".toByteArray()
        val frame = "USLT".toByteArray() + int32(0x7FFFFFFF) + byteArrayOf(0, 0) + payload
        val tag = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + synchsafe(frame.size) + frame
        assertNull(LyricsParser.fromBytes(tag))
    }

    // ---- Vorbis / MP4 --------------------------------------------------------------------------

    @Test fun vorbisLyricsKeyIsRead() {
        val body = "TITLE=Песня\nLYRICS=строка один\nстрока два\n".toByteArray(Charsets.UTF_8)
        val doc = LyricsParser.fromBytes(body)
        assertNotNull(doc)
        assertEquals(LyricsSource.VORBIS, doc!!.source)
        assertEquals(2, doc.lines.size)
    }

    @Test fun mp4LyricAtomIsRead() {
        val payload = "\u0000\u0000\u0000©lyr".toByteArray(Charsets.ISO_8859_1) +
            int32(40) + "тут текст песни целиком".toByteArray(Charsets.UTF_8)
        val doc = LyricsParser.fromBytes(payload)
        assertNotNull(doc)
        assertTrue("found text: ${doc?.lines}", doc!!.lines.joinToString(" ").contains("тут текст"))
    }

    @Test fun savedTextWinsAsSource() {
        val doc = LyricsParser.fromText("[00:03.00]моя строка", LyricsSource.SAVED)!!
        assertEquals(LyricsSource.SAVED, doc.source)
    }
}
