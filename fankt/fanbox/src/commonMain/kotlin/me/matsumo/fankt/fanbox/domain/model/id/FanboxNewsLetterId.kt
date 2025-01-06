package me.matsumo.fankt.fanbox.domain.model.id

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class FanboxNewsLetterId(val value: String) {

    @OptIn(ExperimentalUuidApi::class)
    val uniqueValue: String = "news-$value-${Uuid.random()}"

    override fun toString(): String = value
}
