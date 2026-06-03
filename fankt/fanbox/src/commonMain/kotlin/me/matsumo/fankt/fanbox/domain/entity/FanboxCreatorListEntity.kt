package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * クリエイター一覧 API のレスポンスエンティティ
 *
 * `creator.listFollowing` / `creator.listPixiv` / `creator.listRecommended` で使用される。
 */
@Serializable
internal data class FanboxCreatorListEntity(
    @SerialName("body")
    val body: Body,
) {
    /**
     * レスポンスボディ
     */
    @Serializable
    data class Body(
        @SerialName("creators")
        val creators: List<FanboxCreatorDetailEntity.Body>,
    )
}
