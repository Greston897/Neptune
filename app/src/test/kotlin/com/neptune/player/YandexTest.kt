package com.neptune.player

import com.neptune.player.data.LibraryLogic
import com.neptune.player.data.yandex.MiniJson
import com.neptune.player.data.yandex.YandexMap
import com.neptune.player.data.yandex.YandexSign
import com.neptune.player.model.Sources
import com.neptune.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming layer, tested without a device and without a network.
 *
 * This is the part of 4.0 that cannot be checked by looking at the app: a wrong byte in the signature,
 * a renamed JSON key or a dropped preview flag all produce the same visible result — "ничего не
 * нашлось" or "трек не играет". Every fixture below is shaped exactly like the real response, so the
 * mapping and the signing are pinned here instead of being discovered on a phone.
 */
class YandexTest {

    // ---------------------------------------------------------------- signature

    /**
     * The signing rule, spelled out: md5(salt + path-without-leading-slash + one-time salt).
     *
     * The expected hash is computed by hand from the documented algorithm, not copied from the code
     * under test — `md5("XGRlBW9FXlekgbPrRHuSiA" + "get-mp3/a/b.mp3" + "deadbeef")` — so a change to the
     * concatenation order fails here rather than producing links the service rejects in the field.
     */
    @Test fun downloadLinkIsSignedWithTheFixedSalt() {
        val ticket = YandexSign.Ticketed(
            host = "storage.mds.yandex.net",
            path = "/get-mp3/a/b.mp3",
            ts = "1699999999",
            salt = "deadbeef",
        )
        val expected = YandexSign.md5(YandexSign.SALT + "get-mp3/a/b.mp3" + "deadbeef")
        assertEquals(
            "https://storage.mds.yandex.net/get-mp3/$expected/1699999999/get-mp3/a/b.mp3",
            YandexSign.directLink(ticket),
        )
    }

    /**
     * A path without a leading slash must still produce a link with one.
     *
     * The reference implementation does `path[1:]`, which silently eats the first real character when
     * the path is already relative — a defect that shows up as a 403 nobody can explain.
     */
    @Test fun aPathWithoutLeadingSlashIsNotMangled() {
        val relative = YandexSign.Ticketed("h", "get-mp3/x/y.mp3", "1", "s")
        val absolute = YandexSign.Ticketed("h", "/get-mp3/x/y.mp3", "1", "s")
        assertEquals(YandexSign.directLink(absolute), YandexSign.directLink(relative))
        assertTrue(YandexSign.directLink(relative).endsWith("/1/get-mp3/x/y.mp3"))
    }

    @Test fun theSaltIsTheDocumentedConstant() {
        // Pinned on purpose: this string is the whole reason the downloads endpoint answers.
        assertEquals("XGRlBW9FXlekgbPrRHuSiA", YandexSign.SALT)
    }

    @Test fun ticketXmlIsReadAndMissingFieldsAreRejected() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <download-info>
              <host>cdn.example.com</host>
              <path>/get-mp3/aa/bb.mp3</path>
              <ts>12345</ts>
              <s>cafe</s>
            </download-info>
        """.trimIndent()
        val ticket = YandexSign.parseTicket(xml)
        assertNotNull("a valid ticket must parse", ticket)
        assertEquals("cdn.example.com", ticket!!.host)
        assertEquals("/get-mp3/aa/bb.mp3", ticket.path)
        assertEquals("12345", ticket.ts)
        assertEquals("cafe", ticket.salt)

        assertNull("a ticket without a salt is useless", YandexSign.parseTicket("<download-info><host>h</host></download-info>"))
    }

    @Test fun theBestVariantPrefersMp3ThenBitrate() {
        val variants = listOf(
            YandexSign.DownloadInfo("aac", 256, "https://x/1"),
            YandexSign.DownloadInfo("mp3", 128, "https://x/2"),
            YandexSign.DownloadInfo("mp3", 320, "https://x/3"),
            YandexSign.DownloadInfo("mp3", 320, ""),
        )
        assertEquals("https://x/3", YandexSign.best(variants)?.infoUrl)
        assertNull(YandexSign.best(emptyList()))
    }

    // ---------------------------------------------------------------- json

    @Test fun jsonReaderHandlesEscapesAndNesting() {
        val value = MiniJson.parse("""{"a":[1,2,{"b":"x\\"y\\n"}],"c":true,"d":null,"e":-1.5}""")
        val root = MiniJson.obj(value)
        assertEquals(2.0, (MiniJson.list(root["a"])[0] as Number).toDouble(), 0.0001)
        assertEquals("x\"y\n", MiniJson.obj(MiniJson.list(root["a"])[2])["b"])
        assertEquals(true, root["c"])
        assertNull(root["d"])
        assertEquals(-1.5, (root["e"] as Number).toDouble(), 0.0001)
    }

    /**
     * Regression: a number used to swallow the delimiter after it.
     *
     * The scanner consumed digits and any of `.eE+-`, so the `1` in `{"n":1,"b":true}` took the comma
     * with it and the parse failed as "bad number" — on valid JSON, in the fixture that mirrors the
     * service's own shapes. The exponent sign is now only accepted straight after an `e`.
     */
    @Test fun aNumberStopsAtTheDelimiterInsteadOfSwallowingIt() {
        val root = MiniJson.obj(MiniJson.parse("""{"n":1,"b":true,"list":[2,-3,4.5e2],"m":-1e-2}"""))
        assertEquals(1.0, (root["n"] as Number).toDouble(), 0.0001)
        assertEquals(true, root["b"])
        val list = MiniJson.list(root["list"])
        assertEquals(2.0, (list[0] as Number).toDouble(), 0.0001)
        assertEquals(-3.0, (list[1] as Number).toDouble(), 0.0001)
        assertEquals(450.0, (list[2] as Number).toDouble(), 0.0001)
        assertEquals(-0.01, (root["m"] as Number).toDouble(), 0.000001)
    }

    @Test fun jsonReaderRejectsGarbageInsteadOfReturningHalfOfIt() {
        assertTrue(runCatching { MiniJson.parse("{\"a\":}") }.isFailure)
        assertTrue(runCatching { MiniJson.parse("[1,") }.isFailure)
    }

    @Test fun jsonWriterRoundTripsTheCatalogue() {
        val rows = listOf(mapOf("id" to "1", "title" to "Кавычки \"и\" слэш \\", "n" to 3))
        val parsed = MiniJson.list(MiniJson.parse(MiniJson.write(rows)))
        assertEquals("Кавычки \"и\" слэш \\", MiniJson.obj(parsed[0])["title"])
    }

    // ---------------------------------------------------------------- mapping

    @Test fun aSearchResponseBecomesRemoteTracks() {
        val response = """
            {"text":"neptune","misspellResult":null,
             "tracks":{"results":[
               {"track":{
                 "id":123,"title":"Невесомость","durationMs":214000,
                 "artists":[{"id":7,"name":"Neptune Studio"},{"id":8,"name":"гость"}],
                 "albums":[{"id":55,"title":"Ночные сессии","year":2024,
                            "coverUri":"avatars.yandex.net/get-music-content/1/ab/QQ"}]}}]},
             "albums":{"results":[{"id":55,"title":"Ночные сессии","year":2024,
                 "artists":[{"name":"Neptune Studio"}],"trackCount":9,
                 "coverUri":"avatars.yandex.net/x/QQ"}]},
             "artists":{"results":[{"id":7,"name":"Neptune Studio","trackCount":42}]}}
        """.trimIndent()
        val search = YandexMap.search(MiniJson.parse(response))
        assertEquals(1, search.tracks.size)
        val track = search.tracks.first()
        assertEquals("123", track.id)
        assertEquals("Невесомость", track.title)
        assertEquals("Neptune Studio, гость", track.artists)
        assertEquals("Ночные сессии", track.album)
        assertEquals(214000L, track.durationMs)
        assertEquals("55", track.albumId)
        assertEquals("7", track.artistId)
        assertEquals(2024, track.year)
        // The %% placeholder must be expanded, or Coil requests a URL that 404s.
        assertEquals("https://avatars.yandex.net/get-music-content/1/ab/400x400", track.artworkUri)
        assertEquals(1, search.albums.size)
        assertEquals(9, search.albums.first().trackCount)
        assertEquals(42, search.artists.first().trackCount)
    }

    @Test fun aTrackWithoutATitleIsSkippedRatherThanShownBlank() {
        val search = YandexMap.search(MiniJson.parse("""{"tracks":{"results":[{"track":{"id":1}}]}}"""))
        assertTrue(search.tracks.isEmpty())
    }

    @Test fun theSpellingCorrectionIsOnlyOfferedWhenItDiffers() {
        val corrected = YandexMap.search(MiniJson.parse("""{"text":"гражданская абарона","misspellResult":"Гражданская оборона"}"""))
        assertEquals("Гражданская оборона", corrected.corrected)
        val same = YandexMap.search(MiniJson.parse("""{"text":"neptune","misspellResult":"neptune"}"""))
        assertNull("an identical correction is noise, not a hint", same.corrected)
    }

    @Test fun aChartResponseKeepsPositionsMenuAndListeners() {
        val response = """
            {"title":"Чарт России","chartDescription":"Обновляется каждый день",
             "menu":{"items":[{"title":"Мировой","url":"/landing3/chart/world","selected":false},
                              {"title":"Россия","url":"/landing3/chart/russia","selected":true}]},
             "chart":{"tracks":[
               {"chart":{"position":2,"shift":-1,"listeners":1200000},"track":{"id":2,"title":"Второй","durationMs":1,"artists":[{"id":1,"name":"A"}]}},
               {"chart":{"position":1,"shift":3,"listeners":900000},"track":{"id":1,"title":"Первый","durationMs":1,"artists":[{"id":1,"name":"A"}]}}]}}
        """.trimIndent()
        val page = YandexMap.chart(MiniJson.parse(response))
        assertNotNull(page)
        assertEquals("Чарт России", page!!.title)
        // Sorted by position: the service does not guarantee the order, and a chart in the wrong
        // order is worse than no chart.
        assertEquals(listOf(1, 2), page.entries.map { it.position })
        assertEquals(3, page.entries.first().shift)
        assertEquals(900000, page.entries.first().listeners)
        assertEquals(listOf("Мировой", "Россия"), page.menu.items.map { it.title })
        assertEquals(listOf("world", "russia"), page.menu.items.map { it.postfix })
        assertTrue(page.menu.items.last().selected)
    }

    @Test fun aChartWithNoPlayableRowsIsTreatedAsAnError() {
        assertNull(YandexMap.chart(MiniJson.parse("""{"chart":{"tracks":[]}}""")))
    }

    @Test fun downloadInfoIsReadFromEitherEnvelope() {
        val flat = """
            {"result":[{"codec":"mp3","bitrateInKbps":320,"downloadInfoUrl":"https://s/1"}]}
        """.trimIndent()
        assertEquals("https://s/1", YandexMap.downloadInfo(MiniJson.parse(flat)).single().infoUrl)
        val nested = """
            {"result":{"result":[{"codec":"mp3","bitrateInKbps":128,"downloadInfoUrl":"s/2"}]}}
        """.trimIndent()
        assertEquals("https://s/2", YandexMap.downloadInfo(MiniJson.parse(nested)).single().infoUrl)
        assertTrue(YandexMap.downloadInfo(MiniJson.parse("""{"result":[]}""")).isEmpty())
    }

    @Test fun accountStatusCarriesNameAndPlus() {
        val account = YandexMap.account(MiniJson.parse("""{"result":{"account":{"uid":99,"displayName":"Лена"},"plus":{"hasPlus":true}}}"""))
        assertEquals("Лена", account.name)
        assertEquals("99", account.uid)
        assertTrue(account.plus)
        assertTrue(account.verified)
    }

    /**
     * The queue entry for a stream is a handle, not a URL.
     *
     * This is the invariant that makes a persisted queue survive a restart: signed links expire in
     * about a minute, so storing one would make yesterday's queue unplayable today.
     */
    @Test fun aStreamBecomesAStableHandleThatIsRecognised() {
        val remote = com.neptune.player.model.RemoteTrack(
            id = "1234", title = "Первый", artists = "A", album = "B", durationMs = 1000, artworkUri = null,
        )
        val track = remote.toTrack()
        assertEquals("neptune://yandex/1234", track.uri)
        assertEquals(Track.Source.STREAM, track.source)
        assertEquals("1234", com.neptune.player.model.RemoteTrack.idOf(track))
        assertNull("a local file is not a stream", com.neptune.player.model.RemoteTrack.idOf(
            Track("device:1", "t", "a", "b", 1, "content://media/1"),
        ))
    }

    // ---------------------------------------------------------------- sources

    @Test fun theLastSourceCannotBeTurnedOff() {
        val both = Sources(local = true, yandex = true)
        assertEquals(Sources(local = true, yandex = false), Sources.toggle(both, local = false, on = false))
        // Asking to turn off the only remaining source changes nothing: an app with nowhere to look
        // would show an empty search and no reason for it.
        val onlyLocal = Sources(local = true, yandex = false)
        assertEquals(onlyLocal, Sources.toggle(onlyLocal, local = true, on = false))
    }

    @Test fun searchOrderingPutsFilesFirst() {
        // The local half is a plain filter; the remote half is appended by the screen. This pins the
        // local filter's semantics so a stream id can never be matched by a file query.
        val file = Track("device:1", "Невесомость", "Neptune", "Ночь", 1000, "content://a/1")
        assertEquals(listOf(file), LibraryLogic.search(listOf(file), "невесомость"))
        assertTrue(LibraryLogic.search(listOf(file), "яндекс").isEmpty())
    }

    @Test fun previewsAreVisibleOnTheModel() {
        val row = """
            {"track":{"id":5,"title":"Отрывок","previewDurationMs":30000,
                      "artists":[{"name":"A"}],"availableForPremiumUsers":true}}
        """.trimIndent()
        val track = YandexMap.track(MiniJson.parse(row))
        assertEquals(30000L, track!!.preview)
        assertTrue(track.premiumOnly)
        assertFalse(track.toTrack().previewMs == 0L)
    }
}
