package me.matsumo.fankt.fanbox.components

import me.matsumo.fankt.fanktApplicationContext
import java.io.File

private const val FILE_NAME = "fanbox_session_id.txt"

fun getFanboxSessionId(): String {
    return File(fanktApplicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText().orEmpty()
}

fun setFanboxSessionId(sessionId: String) {
    File(fanktApplicationContext.filesDir, FILE_NAME).writeText(sessionId)
}
