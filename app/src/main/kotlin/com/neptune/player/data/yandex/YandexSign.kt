package com.neptune.player.data.yandex

import java.security.MessageDigest

/**
 * The MD5 signature that turns a `/download-info` response into a playable MP3 URL.
 *
 * Yandex Music answers `/tracks/{id}/download-info` with a *list* of variants (codec + bitrate),
 * each one pointing at an XML document that looks like:
 *
 * ```xml
 * <download-info>
 *   <host>storage.mds.yandex.net</host>
 *   <path>/get-mp3/abc/12345/path/to/file.mp3</path>
 *   <ts>1699999999</ts>
 *   <s>a1b2c3</s>
 * </download-info>
 * ```
 *
 * The `s` value is a one-time salt. The link is only valid for about a minute, and it is signed
 * with `md5(SALT + path-without-leading-slash + s)`:
 *
 * ```
 * https://{host}/get-mp3/{sign}/{ts}{path}
 * ```
 *
 * This is the algorithm the community clients have used for years (MarshalX/yandex-music-api keeps
 * it in `download_info.py`, and every other client copies it), and the salt is fixed because older
 * builds still in the wild must keep working. Both are reproduced here rather than shelled out to a
 * library, because the app needs exactly two URL constructions and no dependency brings them.
 *
 * What is *not* reproduced is any claim that this is supported: see `YandexMusic.kt` and the about
 * screen — this talks to an undocumented endpoint, and a change on the other side breaks it.
 */
object YandexSign {
    /**
     * The fixed download salt.
     *
     * Public knowledge, and deliberately not a secret: it is a constant baked into every Yandex
     * Music client, present in dozens of open repositories. It lives in the APK regardless of how it
     * is written, so obfuscating it would only make the failure harder to read. It is *not* a user
     * credential — a token, when the user adds one, is the only thing here that is.
     */
    const val SALT = "XGRlBW9FXlekgbPrRHuSiA"

    /** Everything the server said about one downloadable file. */
    data class DownloadInfo(
        val codec: String,
        val bitrateKbps: Int,
        /** URL of the XML with host/path/ts/s. Short-lived: fetch it right before playing. */
        val infoUrl: String,
    ) {
        /** Higher is better; used to pick one variant out of the list. */
        val rank: Int get() = bitrateKbps
    }

    /** The four fields the XML carries, already unescaped. */
    data class Ticketed(
        val host: String,
        val path: String,
        val ts: String,
        val salt: String,
    )

    /**
     * Builds the direct link from the ticketed fields.
     *
     * `path[1::]` in the reference implementation is "drop the leading slash", which is what
     * [pathWithoutSlash] does — string concatenation must not depend on the path always starting
     * with `/`, or a variant that omits it would sign a URL that 403s with no way to tell why.
     */
    fun directLink(t: Ticketed): String {
        val sign = md5(SALT + pathWithoutSlash(t.path) + t.salt)
        return "https://${t.host}/get-mp3/$sign/${t.ts}${ensureLeadingSlash(t.path)}"
    }

    /**
     * A stable name for the bytes behind a ticket.
     *
     * The signed URL changes every minute, but the `path` inside it does not: it is the storage
     * object, so hashing it gives an id that survives a re-signature. Downloads name their files with
     * this (never with the URL), which is what lets a saved track be found again after a restart
     * instead of being fetched twice.
     */
    fun fileId(t: Ticketed): String = md5(t.host + pathWithoutSlash(t.path)).take(24)

    fun pathWithoutSlash(path: String) = path.trimStart('/')

    private fun ensureLeadingSlash(path: String) = if (path.startsWith("/")) path else "/$path"

    fun md5(value: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { b -> out.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF]) }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Reads the `/download-info` XML.
     *
     * Hand-parsed with a tolerant tag scan instead of `XmlPullParser`: the document is four leaf
     * elements, and a parser that throws on an unexpected attribute would turn a working stream into
     * "не удалось воспроизвести". Returns null when any required field is missing, which the caller
     * reports as a real error rather than retrying forever.
     */
    fun parseTicket(xml: String): Ticketed? {
        fun tag(name: String): String? {
            val open = "<$name"
            var from = xml.indexOf(open)
            while (from >= 0) {
                val end = xml.indexOf('>', from)
                if (end < 0) return null
                // Skip a self-closing <name/> — it has no text.
                if (xml[end - 1] != '/') {
                    val close = xml.indexOf("</$name>", end)
                    if (close >= 0) return xml.substring(end + 1, close).trim()
                }
                from = xml.indexOf(open, end)
            }
            return null
        }
        return Ticketed(
            host = tag("host") ?: return null,
            path = tag("path") ?: return null,
            ts = tag("ts") ?: return null,
            salt = tag("s") ?: return null,
        )
    }

    /**
     * Picks the best variant a listener will actually get.
     *
     * 320 kbps mp3 is the ceiling the download endpoint serves without a subscription; the list can
     * also contain aac/mp4 variants that ExoPlayer would rather have, but progressive mp3 is the one
     * every device decodes identically, so it wins when present.
     */
    fun best(variants: List<DownloadInfo>): DownloadInfo? = variants
        .filter { it.infoUrl.isNotBlank() && !it.infoUrl.startsWith("encraw") }
        .sortedWith(compareByDescending<DownloadInfo> { it.codec == "mp3" }.thenByDescending { it.rank })
        .firstOrNull()
}
