package com.shadowrp.app

/** Guard asynchronous replies against imports, edited histories and double sends. */
internal object RpReplySafety {
    fun canApply(
        requestedHistory: List<Pair<String, String>>,
        currentHistory: List<Pair<String, String>>?
    ): Boolean = requestedHistory.isNotEmpty() &&
        requestedHistory.last().first == "user" &&
        currentHistory == requestedHistory

    fun clean(raw: String): String {
        val reply = raw.trim()
        require(reply.isNotEmpty()) { "Модель вернула пустой ответ" }
        return reply.take(20000)
    }
}
