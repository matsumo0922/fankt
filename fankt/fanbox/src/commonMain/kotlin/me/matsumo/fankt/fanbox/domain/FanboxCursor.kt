package me.matsumo.fankt.fanbox.domain

import kotlinx.serialization.Serializable

@Serializable
data class FanboxCursor(
    val firstPublishedDatetime: String?,
    val maxPublishedDatetime: String?,
    val firstId: String?,
    val maxId: String?,
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
        firstPublishedDatetime = parameters["firstPublishedDatetime"],
        maxPublishedDatetime = parameters["maxPublishedDatetime"],
        firstId = parameters["firstId"],
        maxId = parameters["maxId"],
        limit = parameters["limit"]?.toInt(),
    )
}
