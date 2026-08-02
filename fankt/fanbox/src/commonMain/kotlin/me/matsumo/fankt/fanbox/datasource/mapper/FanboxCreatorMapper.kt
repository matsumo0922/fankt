@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.createFanboxJson
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
import kotlin.time.Instant

internal class FanboxCreatorMapper(
    private val listItemDecoder: FanboxListItemDecoder = FanboxListItemDecoder(),
    private val formatter: Json = createFanboxJson(),
) {

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

    fun map(entity: FanboxCreatorListEntity, endpoint: String): FanboxTolerantResult<List<FanboxCreatorDetail>> {
        return listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.creators,
            deserializer = FanboxCreatorDetailEntity.Body.serializer(),
        ) { item, _ -> FanboxTolerantResult(map(item), emptyList()) }
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
                profileItems = profileItems.map { rawProfileItem ->
                    val profileItem = formatter.decodeFromJsonElement<FanboxCreatorDetailEntity.Body.ProfileItem>(
                        rawProfileItem,
                    )
                    val serviceProvider = profileItem.serviceProvider
                    val videoId = profileItem.videoId
                    when {
                        profileItem.type == "image" -> FanboxCreatorDetail.ProfileItem.Image(
                            id = profileItem.id,
                            imageUrl = profileItem.imageUrl,
                            thumbnailUrl = profileItem.thumbnailUrl,
                        )

                        profileItem.type == "video" && !serviceProvider.isNullOrBlank() && !videoId.isNullOrBlank() ->
                            FanboxCreatorDetail.ProfileItem.Video(
                                id = profileItem.id,
                                serviceProvider = serviceProvider,
                                videoId = videoId,
                                thumbnailUrl = profileItem.thumbnailUrl,
                            )

                        else -> FanboxCreatorDetail.ProfileItem.Unknown(
                            id = profileItem.id,
                            type = profileItem.type,
                            rawJson = rawProfileItem.toString(),
                        )
                    }
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

    fun map(entity: FanboxCreatorPlanListEntity, endpoint: String): FanboxTolerantResult<List<FanboxCreatorPlan>> {
        return listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.plans,
            deserializer = FanboxCreatorPlanListEntity.Plan.serializer(),
        ) { item, _ -> FanboxTolerantResult(map(item), emptyList()) }
    }

    fun map(entity: FanboxCreatorPlanListEntity.Plan): FanboxCreatorPlan {
        return with(entity) {
            FanboxCreatorPlan(
                coverImageUrl = coverImageUrl,
                description = description,
                fee = fee,
                hasAdultContent = hasAdultContent,
                id = FanboxPlanId(id),
                paymentMethod = FanboxPaymentMethod.fromString(paymentMethod),
                title = title,
                user = user?.let { user ->
                    FanboxUser(
                        userId = FanboxUserId(user.userId.toLong()),
                        creatorId = creatorId?.let { id -> FanboxCreatorId(id) },
                        name = user.name,
                        iconUrl = user.iconUrl,
                    )
                },
            )
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
        return entity.body.featuredTags.map {
            FanboxTag(
                name = it.tag,
                count = it.count,
                coverImageUrl = it.coverImageUrl,
            )
        }
    }
}
