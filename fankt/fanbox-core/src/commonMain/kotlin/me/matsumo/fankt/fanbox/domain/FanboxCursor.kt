package me.matsumo.fankt.fanbox.domain

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.response.FanboxUrlParts

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
    // nextUrl のクエリ値は URL エンコード済みのため、一度だけデコードした値を取得する。
    // 次ページ URL 自体は実行せず、既知の endpoint descriptor を再構築するために使う。
    val url = FanboxUrlParts.parse(this)

    return FanboxCursor(
        firstPublishedDatetime = url.queryValue("firstPublishedDatetime"),
        maxPublishedDatetime = url.queryValue("maxPublishedDatetime"),
        firstId = url.queryValue("firstId"),
        maxId = url.queryValue("maxId"),
        limit = url.queryValue("limit")?.toInt(),
    )
}
