package me.matsumo.fankt.fanbox.components

import android.content.Context
import java.io.File

fun getFanboxSessionId(context: Context): String = context.sessionIdFile()
    .takeIf { it.exists() }
    ?.readText()
    .orEmpty()

fun setFanboxSessionId(context: Context, sessionId: String) {
    context.sessionIdFile().writeText(sessionId)
}

private fun Context.sessionIdFile(): File {
    val appContext = applicationContext
    val filesDir = appContext.filesDir

    return File(filesDir, "fanbox_session_id.txt")
}
