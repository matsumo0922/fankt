package me.matsumo.fankt.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorTagListEntity
import me.matsumo.fankt.domain.entity.FanboxTagListEntity
import me.matsumo.fankt.domain.model.FanboxCreator
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.domain.model.FanboxCreatorTag
import me.matsumo.fankt.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.domain.model.FanboxTag
import me.matsumo.fankt.domain.model.FanboxUser
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPlanId

internal fun FanboxCreatorEntity.translate(): FanboxCreator {
    return FanboxCreator(
        creatorId = creatorId?.let { id -> FanboxCreatorId(id) },
        user = user?.translate()
    )
}

internal fun FanboxCreatorDetailEntity.translate(): FanboxCreatorDetail {
    return body.translate()
}

internal fun FanboxCreatorListEntity.translate(): List<FanboxCreatorDetail> {
    return body.map { it.translate() }
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

internal fun FanboxCreatorPlanListEntity.translate(): List<FanboxCreatorPlan> {
    return body.map {
        FanboxCreatorPlan(
            coverImageUrl = it.coverImageUrl,
            description = it.description,
            fee = it.fee,
            hasAdultContent = it.hasAdultContent,
            id = FanboxPlanId(it.id),
            paymentMethod = FanboxPaymentMethod.fromString(it.paymentMethod),
            title = it.title,
            user = it.user?.translate(),
        )
    }
}

internal fun FanboxCreatorPlanDetailEntity.translate(): FanboxCreatorPlanDetail {
    return FanboxCreatorPlanDetail(
        plan = FanboxCreatorPlan(
            id = FanboxPlanId(body.plan.id),
            title = body.plan.title,
            description = body.plan.description,
            fee = body.plan.fee,
            coverImageUrl = body.plan.coverImageUrl,
            hasAdultContent = body.plan.hasAdultContent,
            paymentMethod = FanboxPaymentMethod.fromString(body.plan.paymentMethod),
            user = body.plan.user?.translate(),
        ),
        supportStartDatetime = body.supportStartDatetime,
        supportTransactions = body.supportTransactions.map {
            FanboxCreatorPlanDetail.SupportTransaction(
                id = it.id,
                paidAmount = it.paidAmount,
                transactionDatetime = Instant.parse(it.transactionDatetime),
                targetMonth = it.targetMonth,
                user = FanboxUser(
                    userId = it.supporter.userId,
                    name = it.supporter.name,
                    iconUrl = it.supporter.iconUrl,
                ),
            )
        },
        supporterCardImageUrl = body.supporterCardImageUrl,
    )
}

internal fun FanboxCreatorTagListEntity.translate(): List<FanboxCreatorTag> {
    return body.map {
        FanboxCreatorTag(
            count = it.count,
            coverImageUrl = it.coverImageUrl,
            tag = it.tag,
        )
    }
}
