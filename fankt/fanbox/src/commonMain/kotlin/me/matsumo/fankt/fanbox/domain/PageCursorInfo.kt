package me.matsumo.fankt.fanbox.domain

import kotlinx.serialization.Serializable

@Serializable
data class PageCursorInfo<T>(
    val contents: List<T>,
    val cursor: me.matsumo.fankt.fanbox.domain.FanboxCursor?,
)

@Serializable
data class PageNumberInfo<T>(
    val contents: List<T>,
    val nextPage: Int?,
)

@Serializable
data class PageOffsetInfo<T>(
    val contents: List<T>,
    val offset: Int?,
)
