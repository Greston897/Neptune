package com.neptune.player

import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.ToastDurationMs
import com.neptune.player.ui.ToastHostState
import com.neptune.player.ui.ToastTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pill notice is the app's main confirmation surface, so its state has to behave when messages
 * arrive faster than they expire — the classic failure is a second message being wiped by the first
 * one's timer.
 */
class ToastTest {

    @Test fun everyNoticeGetsItsOwnId() {
        val host = ToastHostState()
        host.show("Добавлено в очередь")
        val first = host.current.value!!.id
        host.show("Добавлено в очередь")
        val second = host.current.value!!.id
        assertNotEquals("the timer must be able to tell two identical texts apart", first, second)
    }

    @Test fun aStaleTimerCannotClearANewerNotice() {
        val host = ToastHostState()
        host.show("Первый")
        val firstId = host.current.value!!.id
        host.show("Второй")
        host.clear(firstId)
        assertEquals("clearing by id must not remove the newer message", "Второй", host.current.value?.text)
        host.clear(host.current.value?.id)
        assertNull(host.current.value)
    }

    @Test fun toneAndIconAreCarriedThrough() {
        val host = ToastHostState()
        host.show("Нет доступа", NeIcon.Info, ToastTone.WARN)
        val notice = host.current.value!!
        assertEquals(NeIcon.Info, notice.icon)
        assertEquals(ToastTone.WARN, notice.tone)
    }

    @Test fun pillBorrowsItselfForTwoSeconds() {
        assertEquals("the brief asked for 2 s", 2000L, ToastDurationMs)
    }
}
