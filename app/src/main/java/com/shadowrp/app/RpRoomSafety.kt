package com.shadowrp.app

/** Pure safeguards for asynchronous multi-character RP room replies.
 * A response belongs to the exact persisted conversation that requested it.
 * This prevents a delayed model result from overwriting newer player actions.
 */
internal object RpRoomSafety {
    fun canApplyReply(expectedHistory: String?, currentHistory: String?): Boolean =
        expectedHistory != null && expectedHistory == currentHistory

    fun canRetry(lastRole: String?, generating: Boolean): Boolean =
        !generating && lastRole == "Игрок"
}
