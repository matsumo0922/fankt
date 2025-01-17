package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorTagListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCreator
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.FanboxTag
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPlanId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

internal class FanboxCreatorMapper {

    fun map(entity: FanboxCreatorEntity): FanboxCreator {
        return FanboxCreator(
            creatorId = FanboxCreatorId(entity.creatorId.orEmpty()),
            user = entity.user?.let {
                FanboxUser(
                    userId = FanboxUserId(it.userId.toLong()),
                    creatorId = entity.creatorId?.let { id -> FanboxCreatorId(id) },
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            },
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
                        userId = FanboxUserId(it.userId.toLong()),
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
                            userId = FanboxUserId(user.userId.toLong()),
                            creatorId = it.creatorId?.let { id -> FanboxCreatorId(id) },
                            name = user.name,
                            iconUrl = user.iconUrl,
                        )
                    },
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
                            userId = FanboxUserId(it.userId.toLong()),
                            creatorId = plan.creatorId?.let { id -> FanboxCreatorId(id) },
                            name = it.name,
                            iconUrl = it.iconUrl,
                        )
                    },
                ),
                supportStartDatetime = supportStartDatetime,
                supportTransactions = supportTransactions.map {
                    FanboxCreatorPlanDetail.SupportTransaction(
                        id = it.id,
                        paidAmount = it.paidAmount,
                        transactionDatetime = Instant.parse(it.transactionDatetime),
                        targetMonth = it.targetMonth,
                        user = FanboxUser(
                            userId = FanboxUserId(it.supporter.userId.toLong()),
                            creatorId = plan.creatorId?.let { id -> FanboxCreatorId(id) },
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
