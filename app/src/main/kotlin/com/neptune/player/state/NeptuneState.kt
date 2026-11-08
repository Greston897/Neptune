package com.neptune.player.state

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.neptune.player.AppContainer
import com.neptune.player.NeptuneApplication
import com.neptune.player.data.AvatarStore
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.Features
import com.neptune.player.model.LyricsDoc
import com.neptune.player.model.PlanFeature
import com.neptune.player.model.PlanTier
import com.neptune.player.model.Pricing
import com.neptune.player.model.Settings
import com.neptune.player.model.Track
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.ToastHostState
import com.neptune.player.ui.ToastTone

/** UI coordinator. Playback, metadata and persistence live behind repository boundaries. */
class NeptuneState(application: Application) : AndroidViewModel(application) {
    private val container = (application as NeptuneApplication).container
    val settings = container.settings.state
    val users = container.users.state
    val library = container.library.state
    val playback = container.playback.state
    val plan = container.plan.tier
    val annualPlan = container.plan.annual

    /**
     * The single confirmation surface. Every "Добавлено…", "Сохранено", "Убрано" goes through here
     * and appears inside the now-playing pill; the old snackbar channel is gone, because a notice at
     * the bottom of the screen sits on top of the transport and far from the object it describes.
     */
    val toast = ToastHostState()
    var search by mutableStateOf("")
    var libraryTab by mutableStateOf(0)
    var librarySort by mutableStateOf(0)
    var contextTrack by mutableStateOf<Track?>(null)
    var contextPlaylist by mutableStateOf<String?>(null)

    /** Paywall destination requested by a gated control; the host opens the subscription screen. */
    /**
     * Why the plan page is open, when a specific control asked for it. Null is a legitimate reason:
     * the user simply pressed "Neptune Plus" to read about the plan.
     */
    var paywall by mutableStateOf<PlanFeature?>(null)
        private set

    /**
     * Whether the plan page is open at all.
     *
     * Kept apart from [paywall] because "no particular reason" and "not open" are different states,
     * and folding them into one nullable field made a tap on "Neptune Plus" open nothing at all.
     * This is the single source of truth for the overlay; only [gate], [openPaywall], [closePaywall]
     * and [subscribe] write it.
     */
    var paywallOpen: Boolean by mutableStateOf(false)
        private set

    /**
     * What the user asked for and Plus refused, so it can be handed back the moment they subscribe.
     *
     * Without this the upgrade ends in a toast and a dead end: the user tapped "Загрузить фото",
     * bought Plus, and now has to find that button again. The action is *replayed*, never stored —
     * a pending closure that is never consumed is dropped when the paywall closes.
     */
    private var paywallIntent: (() -> Unit)? = null

    /** Track whose text is being imported from a file; the host's picker reads and clears it. */
    var pendingLyricsTrack by mutableStateOf<Track?>(null)

    /**
     * Track the lyrics screen was opened *for*, when that is not the one playing.
     *
     * The sheet can ask for any track's text; without this the screen would always show whatever is
     * currently in the transport, which is not what the user tapped. Null means "follow playback".
     */
    var lyricsTarget by mutableStateOf<Track?>(null)

    /** Lyrics known so far, keyed by track id — observed by the lyrics screen. */
    val lyricsDocs = container.lyrics.docs

    /** Opens the text screen: for [track] when given, for the playing track otherwise. */
    fun openLyrics(track: Track? = null) { lyricsTarget = track }

    /**
     * Text the user typed while Plus said no: shown now, not stored.
     *
     * Handed back with the editor's draft, so reopening the screen puts the same words in the field
     * the way a saved text would. Held per track id and cleared when it is finally saved.
     */
    var localLyrics by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun rememberDraft(track: Track, text: String) {
        localLyrics = localLyrics + (track.id to text)
    }

    fun draftFor(track: Track): String? = localLyrics[track.id]

    fun requestLyrics(track: Track) = container.lyrics.request(track.id, track.uri)
    fun lyricsKnown(track: Track) = container.lyrics.isKnown(track.id)
    fun requestLyricsFile(track: Track) { pendingLyricsTrack = track }

    /**
     * Single write path for settings that are gated by the plan.
     *
     * A screen never decides on its own whether a feature is unlocked: it calls [gate] first, and
     * the store is only touched when the tier allows it. That keeps the entitlement rule in one
     * place instead of being re-implemented in every appearance row.
     */
    fun edit(block: (Settings) -> Settings) = container.settings.edit(block)

    val tier: PlanTier get() = plan.value
    fun allows(feature: PlanFeature) = Features.has(feature, tier)

    /**
     * Runs [action] when the tier allows [feature]; otherwise opens the paywall for it.
     * Returns true when the action actually ran, so callers can skip their own feedback.
     *
     * The paywall is *one* overlay, not a stack: asking for a second Plus feature while the plan
     * screen is already open only swaps the reason shown at the top. Two overlapping copies used to
     * draw their blurred panels over one another and read as a smeared double exposure.
     */
    fun gate(feature: PlanFeature, action: () -> Unit): Boolean {
        if (allows(feature)) { action(); return true }
        // A gate always states *its* reason, even over an already open manager page: that is the
        // only case where swapping the reason is correct rather than surprising.
        paywall = feature
        paywallOpen = true
        paywallIntent = action
        // Named the feature instead of saying "это в Plus" into the void: the notice now explains
        // why the tap did nothing at all.
        toast("«${feature.title}» — в Plus", NeIcon.Sparkle, ToastTone.INFO)
        return false
    }

    /**
     * Opens the plan page for a tap that is *not* gated — "Neptune Plus" in Settings or on the
     * profile card. No intent is recorded: there is nothing to finish later, and subscribing from
     * here must simply show the plan as active.
     */
    fun openPlanManager() = openPaywall()

    /**
     * Opens the plan screen from a navigation entry point.
     *
     * [feature] is the reason; without one the screen simply opens as a plan manager. Opening it
     * again while it is already open changes nothing — there is exactly one overlay, and a second
     * copy would draw a second set of blurred panels on top of the first.
     *
     * `paywall` is therefore the single source of truth for "is the plan screen open": it is set
     * only here and in [gate], and cleared only by [closePaywall] or [subscribe]. A `null` here
     * means "closed", never "some default feature".
     */
    fun openPaywall(feature: PlanFeature? = null) {
        // One rule, tested off-device: a second entry point while the page is up changes nothing, so
        // no copy can ever be composed underneath another.
        if (AppContainer.Paywall.openFor(feature)) {
            paywall = feature
            paywallIntent = null
            paywallOpen = true
        }
    }

    /**
     * Closes the plan screen. Anything the user came here to do but did not buy is forgotten here,
     * so a later upgrade cannot replay a stale intention minutes after the fact.
     */
    fun closePaywall() {
        AppContainer.Paywall.close()
        paywallOpen = false
        paywall = null
        paywallIntent = null
    }

    fun setAnnualPlan(annual: Boolean) = container.plan.set(tier, annual)

    fun subscribe(annual: Boolean) {
        container.plan.subscribe(annual)
        toast("Neptune Plus активен", NeIcon.Sparkle, ToastTone.DONE)
        // The reason the screen opened is now allowed: finish it instead of making the user hunt for
        // the button they already pressed. The page state is cleared first so the replayed action
        // (a picker, a dialog, a save) lands on the screen underneath rather than on the paywall.
        val pending = paywallIntent
        AppContainer.Paywall.close()
        paywallIntent = null
        paywallOpen = false
        paywall = null
        pending?.let { it() }
    }

    fun cancelSubscription() {
        container.plan.cancel(container.settings)
        toast("Подписка отключена", NeIcon.Check, ToastTone.INFO)
    }

    /** Price string for the plan, used by the profile card and the paywall header. */
    fun planPrice(): String = if (annualPlan.value) Pricing.yearlyLabel() else Pricing.monthlyLabel()
    /**
     * Resets the look. The confirmation lands in the top pill, which is why this is one of the flows
     * that broke when the pill was limited to tab routes: it was triggered from "Внешний вид" and the
     * notice had nowhere to appear.
     */
    fun resetAppearance() {
        edit { it.resetAppearance() }
        toast("Внешний вид восстановлен", NeIcon.Reset)
    }
    fun refreshLibrary() = container.library.refresh()
    fun hasAudioPermission() = container.library.hasPermission()
    /** Transient confirmation in the top pill. See [ToastHostState]. */
    fun toast(text: String, icon: NeIcon = NeIcon.Check, tone: ToastTone = ToastTone.DONE) = toast.show(text, icon, tone)
    fun play(tracks: List<Track>, index: Int = 0, shuffled: Boolean = false) {
        if (tracks.isEmpty()) { toast("Сначала добавьте музыку", NeIcon.Info, ToastTone.WARN); return }
        container.playback.play(tracks, index, shuffled)
    }
    fun playTrack(track: Track, context: List<Track>) {
        val queue = if (context.any { it.id == track.id }) context else listOf(track)
        play(queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }
    fun togglePlayback() = container.playback.toggle()
    fun nextTrack() = container.playback.next()
    fun previousTrack() = container.playback.previous()
    fun toggleShuffle() = container.playback.toggleShuffle()
    fun cycleRepeat() = container.playback.cycleRepeat()
    fun setVolume(v: Float) = container.playback.setVolume(v)
    /**
     * The fade is a Plus behaviour, but the timer itself is not: a free user still gets a sleep
     * timer, it just stops instead of fading. Nothing here can fail to work on the free tier.
     */
    fun setSleepTimer(minutes: Int) {
        val fade = allows(PlanFeature.SleepFade) && container.settings.state.value.sleepFade
        container.playback.setSleepTimer(minutes, fade)
        if (minutes > 0) toast(if (fade) "Засыпай: музыка затихнет" else "Таймер сна включён", NeIcon.Timer)
        else toast("Таймер сна выключен", NeIcon.Timer, ToastTone.INFO)
    }
    fun retryPlayback() = container.playback.retry()
    fun seek(fraction: Float) = container.playback.seek(fraction)
    fun like(track: Track) = container.users.toggleLike(track.id)
    fun submitSearch() = container.users.rememberSearch(search)
    fun clearSearches() = container.users.clearSearches()
    fun clearHistory() { container.users.clearHistory(); toast("История очищена", NeIcon.Check) }
    fun createPlaylist(name: String) = container.users.createPlaylist(name).also {
        toast("Плейлист «${it.name}» создан", NeIcon.Plus)
    }
    fun renamePlaylist(id: String, name: String) { container.users.renamePlaylist(id, name); toast("Плейлист переименован", NeIcon.Edit) }
    fun deletePlaylist(id: String) { container.users.deletePlaylist(id); toast("Плейлист удалён", NeIcon.Trash) }
    fun addToPlaylist(id: String, track: Track) {
        container.users.addToPlaylist(id, track.id); toast("Добавлено в плейлист", NeIcon.Check)
    }
    fun removeFromPlaylist(id: String, track: Track) {
        container.users.removeFromPlaylist(id, track.id); toast("Убрано из плейлиста", NeIcon.Trash)
    }
    fun playNext(track: Track) { container.playback.playNext(track); toast("Будет играть следующим", NeIcon.Next) }
    fun enqueue(track: Track) { container.playback.enqueue(track); toast("Добавлено в очередь", NeIcon.Queue) }

    /**
     * Deletes a track from the library *record*, not from the phone.
     *
     * For an imported file the picker grant is released and the entry is forgotten, so the track
     * leaves the app without touching the user's file. A device (MediaStore) track cannot be hidden
     * from the app at all, which is why the sheet only offers this action for imported files and
     * says so for device ones.
     */
    fun removeTrack(track: Track) {
        container.library.forget(track)
        container.playback.dropTrack(track.id)
        toast("Трек убран из приложения", NeIcon.Trash)
    }

    /**
     * Stores a picked picture as the profile avatar.
     *
     * The plan is checked *after* the pick, never before the picker: the picture is already in the
     * user's hand by then, so it is copied to private storage and the upgrade offer explains that it
     * will be shown once Plus is on. Nothing is discarded, and subscribing applies it immediately
     * instead of asking the user to pick the same photo again.
     *
     * The *failure* modes stay outside the gate: an unreadable pick keeps the old avatar and says
     * why, and the version counter is bumped so the image loader cannot serve the previous file from
     * its memory cache under the old key.
     */
    fun setAvatarPhoto(uri: Uri) {
        // The picture is already in hand: refusing it after the user navigated a whole file browser
        // would be the rude version of the gate. It is stored, then the paywall explains that the
        // photo shows once Plus is on — never a silent no-op and never a discarded pick.
        val photoIncluded = allows(PlanFeature.AvatarPhoto)
        val name = AvatarStore.save(getApplication(), uri)
        if (name == null) {
            toast("Не удалось прочитать изображение", NeIcon.Info, ToastTone.WARN)
            return
        }
        if (!photoIncluded) {
            // Keep the file, keep it out of the avatar slot: nothing is lost if Plus arrives later.
            toast("Фото выбрано — покажем его в Plus", NeIcon.Sparkle, ToastTone.INFO)
            paywall = PlanFeature.AvatarPhoto
            paywallOpen = true
            paywallIntent = {
                edit { it.copy(avatarPhoto = name, avatarPhotoVersion = it.avatarPhotoVersion + 1) }
                toast("Фото профиля обновлено", NeIcon.Check)
            }
            return
        }
        edit { it.copy(avatarPhoto = name, avatarPhotoVersion = it.avatarPhotoVersion + 1) }
        toast("Фото профиля обновлено", NeIcon.Check)
    }

    fun clearAvatarPhoto() {
        edit { it.copy(avatarPhoto = "", avatarPhotoVersion = it.avatarPhotoVersion + 1, avatarZoom = 1f) }
        toast("Снова фирменный знак", NeIcon.Orbit)
    }

    /**
     * Lyrics of the current track, keyed by track id. Free: karaoke is part of the player.
     *
     * Returns the *document*, so callers get the timings as well as the text — a String here would
     * throw away exactly the part that makes the highlight possible.
     */
    fun lyricsFor(track: Track?): LyricsDoc? = track?.let { container.lyrics.get(it.id) }

    /** Saving an edited text is a Plus feature, and the gate is checked here, not in the UI. */
    fun saveLyrics(track: Track, text: String) {
        // Same rule as the photo: text the user already typed is shown immediately and only its
        // *saving* waits for Plus. Showing the dialog's promise, swallowing the save and saying
        // "это в Plus" in a toast would read as a bug, so the words appear and the offer explains
        // why they are not stored yet.
        if (allows(PlanFeature.LyricsSave)) {
            container.lyrics.set(track.id, text)
            localLyrics = localLyrics - track.id
            toast("Текст сохранён", NeIcon.Check)
            return
        }
        container.lyrics.setLocal(track.id, text)
        rememberDraft(track, text)
        toast("Текст показан — сохранение в Plus", NeIcon.Sparkle, ToastTone.INFO)
        paywall = PlanFeature.LyricsSave
        paywallOpen = true
        paywallIntent = {
            container.lyrics.set(track.id, text)
            localLyrics = localLyrics - track.id
            toast("Текст сохранён", NeIcon.Check)
        }
    }
    fun sorted(tracks: List<Track>) = when (librarySort) {
        1 -> tracks.sortedBy { it.artist.lowercase() }
        2 -> tracks.sortedByDescending { it.durationMs }
        else -> tracks.sortedBy { it.title.lowercase() }
    }
    fun search(tracks: List<Track>) = LibraryLogic.search(tracks, search)
    fun playbackJump(index: Int) = container.playback.jumpTo(index)
    fun moveQueue(from: Int, to: Int) = container.playback.move(from, to)
    fun removeQueue(index: Int) = container.playback.remove(index)
    fun clearQueue() = container.playback.clear()
    fun persist() = container.playback.persist()
    override fun onCleared() { persist(); super.onCleared() }
}
