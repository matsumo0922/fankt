package me.matsumo.fankt.fanbox

expect suspend fun getFanboxSessionId(): String

expect suspend fun setFanboxSessionId(sessionId: String)