package com.neptune.player.ui

import androidx.annotation.DrawableRes
import com.neptune.player.R

/**
 * Icon set backed by Lucide (ISC licence, see NOTICE.md) converted to VectorDrawable.
 *
 * Every glyph shares the upstream 24×24 optical grid, a 2dp stroke and round caps/joins, so
 * icons finally sit on the same baseline and carry the same visual weight. The previous
 * hand-authored paths mixed 1.9–2.2 strokes and off-centre geometry, which is why some icons
 * looked squashed or drifted inside their buttons.
 *
 * `bold` twins exist for the tab bar: the active tab gains real optical weight instead of
 * relying on colour alone.
 */
enum class NeIcon(@DrawableRes val res: Int, @DrawableRes val boldRes: Int = res) {
    Play(R.drawable.ne_play),
    Pause(R.drawable.ne_pause),
    Next(R.drawable.ne_next),
    Prev(R.drawable.ne_prev),
    Shuffle(R.drawable.ne_shuffle),
    Repeat(R.drawable.ne_repeat),
    RepeatOne(R.drawable.ne_repeat_one),
    Heart(R.drawable.ne_heart),
    HeartFill(R.drawable.ne_heart_fill),
    Home(R.drawable.ne_home, R.drawable.ne_home_bold),
    Search(R.drawable.ne_search, R.drawable.ne_search_bold),
    Library(R.drawable.ne_library, R.drawable.ne_library_bold),
    Sliders(R.drawable.ne_sliders),
    ChevronDown(R.drawable.ne_chevron_down),
    ChevronUp(R.drawable.ne_chevron_up),
    More(R.drawable.ne_more),
    Queue(R.drawable.ne_queue),
    Volume(R.drawable.ne_volume),
    Mute(R.drawable.ne_mute),
    Mic(R.drawable.ne_mic),
    Share(R.drawable.ne_share),
    Plus(R.drawable.ne_plus),
    Close(R.drawable.ne_close),
    Check(R.drawable.ne_check),
    Sparkle(R.drawable.ne_sparkle),
    Wave(R.drawable.ne_wave),
    Disc(R.drawable.ne_disc),
    Artist(R.drawable.ne_artist),
    Equalizer(R.drawable.ne_equalizer),
    Moon(R.drawable.ne_moon),
    Reset(R.drawable.ne_reset),
    Back(R.drawable.ne_back),
    Settings(R.drawable.ne_settings, R.drawable.ne_settings_bold),
    Clock(R.drawable.ne_clock),
    Folder(R.drawable.ne_folder),
    Music(R.drawable.ne_music),
    Palette(R.drawable.ne_palette),
    Info(R.drawable.ne_info),
    Timer(R.drawable.ne_timer),
    Speed(R.drawable.ne_speed),
    ArrowUp(R.drawable.ne_arrow_up),
    ArrowDown(R.drawable.ne_arrow_down),
    Edit(R.drawable.ne_edit),
    Trash(R.drawable.ne_trash),
    Sort(R.drawable.ne_sort),
    Device(R.drawable.ne_device),
    Bell(R.drawable.ne_bell),
    ChevronRight(R.drawable.ne_chevron_right),
    Logout(R.drawable.ne_logout),
    /** Brand mark: the same orbit glyph the launcher icon uses. */
    Orbit(R.drawable.ne_orbit),
    /** Charts: a rising line, the one glyph that reads as "positions" at 16dp. */
    Trending(R.drawable.ne_trending_up),
    /** A streamed track: the catalogue is online, so the globe says more than a cloud would. */
    Globe(R.drawable.ne_globe),
    /** Download for offline listening. */
    Download(R.drawable.ne_cloud_download),
    /** The account row: paste a token / connect. */
    Login(R.drawable.ne_log_in),
}
