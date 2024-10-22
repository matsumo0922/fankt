package me.matsumo.fankt.datasource.mapper

import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.domain.entity.FanboxTagListEntity
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.FanboxTag

internal fun FanboxCreatorSearchListEntity.translate(): PageNumberInfo<FanboxCreatorDetail> {
    return PageNumberInfo(
        contents = body.creators.map { it.translate() },
        nextPage = body.nextPage,
    )
}

internal fun FanboxTagListEntity.translate(): List<FanboxTag> {
    return body.map {
        FanboxTag(
            count = it.count,
            tag = it.value,
        )
    }
}
