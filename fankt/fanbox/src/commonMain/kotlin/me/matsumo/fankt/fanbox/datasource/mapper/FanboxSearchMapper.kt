package me.matsumo.fankt.fanbox.datasource.mapper

import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxTagListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxTag

internal class FanboxSearchMapper(
    private val creatorMapper: FanboxCreatorMapper,
) {
    fun map(entity: FanboxCreatorSearchListEntity): PageNumberInfo<FanboxCreatorDetail> {
        return PageNumberInfo(
            contents = entity.body.creators.map { creatorMapper.map(it) },
            nextPage = entity.body.nextPage,
        )
    }

    fun map(entity: FanboxTagListEntity): List<FanboxTag> {
        return entity.body.map {
            FanboxTag(
                name = it.value,
                count = it.count,
                coverImageUrl = null,
            )
        }
    }
}
