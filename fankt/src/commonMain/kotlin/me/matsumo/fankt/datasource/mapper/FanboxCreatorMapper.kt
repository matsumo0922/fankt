package me.matsumo.fankt.datasource.mapper

import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.domain.model.FanboxCreator
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.id.FanboxCreatorId

internal fun FanboxCreatorEntity.translate(): FanboxCreator {
    return FanboxCreator(
        creatorId = creatorId?.let { id -> FanboxCreatorId(id) },
        user = user?.translate()
    )
}

internal fun FanboxCreatorDetailEntity.Body.translate(): FanboxCreatorDetail {
    return FanboxCreatorDetail(
        creatorId = FanboxCreatorId(creatorId),
        coverImageUrl = coverImageUrl,
        description = description,
        hasAdultContent = hasAdultContent,
        hasBoothShop = hasBoothShop,
        isAcceptingRequest = isAcceptingRequest,
        isFollowed = isFollowed,
        isStopped = isStopped,
        isSupported = isSupported,
        profileItems = profileItems.map { profileItem ->
            FanboxCreatorDetail.ProfileItem(
                id = profileItem.id,
                imageUrl = profileItem.imageUrl,
                thumbnailUrl = profileItem.thumbnailUrl,
                type = profileItem.type,
            )
        },
        profileLinks = profileLinks.map { profileLink ->
            FanboxCreatorDetail.ProfileLink(
                url = profileLink,
                link = FanboxCreatorDetail.Platform.fromUrl(profileLink),
            )
        },
        user = user?.translate(),
    )
}

internal fun FanboxCreatorSearchListEntity.translate(): PageNumberInfo<FanboxCreatorDetail> {
    return PageNumberInfo(
        contents = body.creators.map { it.translate() },
        nextPage = body.nextPage,
    )
}
