package me.matsumo.fankt.fanbox.components

import android.content.Context
import java.io.File

private const val FILE_NAME = "fanbox_session_id.txt"

fun getFanboxSessionId(context: Context): String {
    return File(context.applicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText().orEmpty()
}

fun setFanboxSessionId(context: Context, sessionId: String) {
    File(context.applicationContext.filesDir, FILE_NAME).writeText(sessionId)
}
