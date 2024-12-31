package me.matsumo.fankt.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorTagListEntity
import me.matsumo.fankt.domain.model.FanboxCreator
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.domain.model.FanboxTag
import me.matsumo.fankt.domain.model.FanboxUser
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPlanId

internal class FanboxCreatorMapper {

    fun map(entity: FanboxCreatorEntity): FanboxCreator {
        return FanboxCreator(
            creatorId = FanboxCreatorId(entity.creatorId.orEmpty()),
            user = entity.user?.let {
                FanboxUser(
                    userId = it.userId,
                    creatorId = FanboxCreatorId(entity.creatorId.orEmpty()),
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            }
        )
    }

    fun map(entity: FanboxCreatorDetailEntity): FanboxCreatorDetail {
        return map(entity.body)
    }

    fun map(entity: FanboxCreatorListEntity): List<FanboxCreatorDetail> {
        return entity.body.map { map(it) }
    }

    fun map(entity: FanboxCreatorDetailEntity.Body): FanboxCreatorDetail {
        return with(entity) {
            FanboxCreatorDetail(
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
                user = user?.let {
                    FanboxUser(
                        userId = it.userId,
                        creatorId = FanboxCreatorId(creatorId),
                        name = it.name,
                        iconUrl = it.iconUrl,
                    )
                },
            )
        }
    }

    fun map(entity: FanboxCreatorPlanListEntity): List<FanboxCreatorPlan> {
        return entity.body.map {
            with(it) {
                FanboxCreatorPlan(
                    coverImageUrl = coverImageUrl,
                    description = description,
                    fee = fee,
                    hasAdultContent = it.hasAdultContent,
                    id = FanboxPlanId(it.id),
                    paymentMethod = FanboxPaymentMethod.fromString(it.paymentMethod),
                    title = it.title,
                    user = it.user?.let { user ->
                        FanboxUser(
                            userId = user.userId,
                            creatorId = FanboxCreatorId(it.creatorId.orEmpty()),
                            name = user.name,
                            iconUrl = user.iconUrl,
                        )
                    }
                )
            }
        }
    }

    fun map(entity: FanboxCreatorPlanDetailEntity): FanboxCreatorPlanDetail {
        return with(entity.body) {
            FanboxCreatorPlanDetail(
                plan = FanboxCreatorPlan(
                    id = FanboxPlanId(plan.id),
                    title = plan.title,
                    description = plan.description,
                    fee = plan.fee,
                    coverImageUrl = plan.coverImageUrl,
                    hasAdultContent = plan.hasAdultContent,
                    paymentMethod = FanboxPaymentMethod.fromString(plan.paymentMethod),
                    user = plan.user?.let {
                        FanboxUser(
                            userId = it.userId,
                            creatorId = FanboxCreatorId(plan.creatorId.orEmpty()),
                            name = it.name,
                            iconUrl = it.iconUrl,
                        )
                    }
                ),
                supportStartDatetime = supportStartDatetime,
                supportTransactions = supportTransactions.map {
                    FanboxCreatorPlanDetail.SupportTransaction(
                        id = it.id,
                        paidAmount = it.paidAmount,
                        transactionDatetime = Instant.parse(it.transactionDatetime),
                        targetMonth = it.targetMonth,
                        user = FanboxUser(
                            userId = it.supporter.userId,
                            creatorId = FanboxCreatorId(plan.creatorId.orEmpty()),
                            name = it.supporter.name,
                            iconUrl = it.supporter.iconUrl,
                        ),
                    )
                },
                supporterCardImageUrl = supporterCardImageUrl,
            )
        }
    }

    fun map(entity: FanboxCreatorTagListEntity): List<FanboxTag> {
        return entity.body.map {
            FanboxTag(
                name = it.tag,
                count = it.count,
                coverImageUrl = it.coverImageUrl,
            )
        }
    }
}