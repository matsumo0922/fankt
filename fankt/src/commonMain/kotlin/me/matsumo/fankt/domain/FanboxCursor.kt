package me.matsumo.fankt.domain

import kotlinx.serialization.Serializable

@Serializable
data class FanboxCursor(
    val maxPublishedDatetime: String,
    val maxId: String,
    val limit: Int?,
)

internal fun String.translateToCursor(): FanboxCursor {
    val parameters = this.substringAfter("?")
        .split("&")
        .associate {
            val (key, value) = it.split("=")
            key to value
        }

    return FanboxCursor(
        maxPublishedDatetime = parameters["maxPublishedDatetime"]!!,
        maxId = parameters["maxId"]!!,
        limit = parameters["limit"]?.toInt(),
    )
}
