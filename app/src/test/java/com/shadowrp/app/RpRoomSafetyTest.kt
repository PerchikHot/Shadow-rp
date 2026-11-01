package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpRoomSafetyTest {
    @Test fun replyAcceptedOnlyForUnchangedHistory() {
        val snapshot = """[{"role":"Игрок","text":"Входим в замок"}]"""
        assertTrue(RpRoomSafety.canApplyReply(snapshot, snapshot))
        assertFalse(RpRoomSafety.canApplyReply(snapshot, snapshot + " "))
        assertFalse(RpRoomSafety.canApplyReply(snapshot, """[{"role":"Игрок","text":"Уходим"}]"""))
        assertFalse(RpRoomSafety.canApplyReply(null, snapshot))
    }

    @Test fun failedRequestCanRetryWithoutNewPlayerTurn() {
        assertTrue(RpRoomSafety.canRetry("Игрок", false))
        assertFalse(RpRoomSafety.canRetry("Игрок", true))
        assertFalse(RpRoomSafety.canRetry("Персонажи", false))
        assertFalse(RpRoomSafety.canRetry(null, false))
    }
}
