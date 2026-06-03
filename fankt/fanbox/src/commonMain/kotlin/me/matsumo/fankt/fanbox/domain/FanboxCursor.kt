package me.matsumo.fankt.fanbox.domain

import io.ktor.http.Url
import kotlinx.serialization.Serializable

/**
 * ページング用のカーソル
 *
 * FANBOX API の `nextUrl` などに含まれるクエリパラメータを保持し、次ページ取得時のリクエストに用いる。
 */
@Serializable
data class FanboxCursor(
    val firstPublishedDatetime: String?,
    val maxPublishedDatetime: String?,
    val firstId: String?,
    val maxId: String?,
    val limit: Int?,
)

internal fun String.translateToCursor(): FanboxCursor {
    // nextUrl のクエリ値は URL エンコード済みのため、Url でパースしてデコードした値を取得する。
    // 手動分割では値をデコードできず、リクエスト時に再エンコードされて二重エンコードになる。
    val parameters = Url(this).parameters

    return FanboxCursor(
        firstPublishedDatetime = parameters["firstPublishedDatetime"],
        maxPublishedDatetime = parameters["maxPublishedDatetime"],
        firstId = parameters["firstId"],
        maxId = parameters["maxId"],
        limit = parameters["limit"]?.toInt(),
    )
}
