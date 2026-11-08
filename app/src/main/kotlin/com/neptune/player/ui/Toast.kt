package com.neptune.player.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A single transient confirmation ("Добавлено в очередь"), shown *inside the now-playing pill*.
 *
 * Why not a snackbar: the snackbar lived at the bottom of the screen, far from the object the
 * message is about, and it covered the dock controls while it was up. The pill already sits where
 * the user is looking and already owns "what is happening right now", so a confirmation borrowed its
 * exact geometry for two seconds and then gives the slot back to the track.
 *
 * [id] increments on every event so two identical texts in a row are still two distinct animations
 * instead of one that silently restarts nothing.
 */
data class ToastNotice(
    val id: Long = 0,
    val text: String = "",
    val icon: NeIcon = NeIcon.Check,
    val tone: ToastTone = ToastTone.DONE,
)

enum class ToastTone { DONE, INFO, WARN }

/**
 * The toast surface, shared by the app chrome and by [notice] call sites.
 *
 * State is a Compose-observable [MutableStateFlow] updated from the same state object every screen
 * already has, so no screen needs its own timer, its own coroutine or its own overlay.
 */
class ToastHostState {
    private var counter = 0L
    val current = MutableStateFlow<ToastNotice?>(null)

    fun show(text: String, icon: NeIcon = NeIcon.Check, tone: ToastTone = ToastTone.DONE) {
        counter += 1
        current.value = ToastNotice(counter, text, icon, tone)
    }

    fun clear(id: Long? = null) {
        current.update { if (id == null || it?.id == id) null else it }
    }
}

/** How long a confirmation borrows the pill. Both preview and the real timer read this. */
const val ToastDurationMs = 2000L
