package me.matsumo.fankt.fanbox.datasource.mapper

import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListStrictEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxBell
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData
import me.matsumo.fankt.fanbox.domain.model.FanboxNewsLetter
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

internal class FanboxUserMapper(
    private val postMapper: FanboxPostMapper,
    private val creatorMapper: FanboxCreatorMapper,
    private val listItemDecoder: FanboxListItemDecoder = FanboxListItemDecoder(),
) {
    fun map(entity: FanboxCreatorPlanListEntity, endpoint: String): FanboxTolerantResult<List<FanboxCreatorPlan>> {
        return creatorMapper.map(entity, endpoint)
    }

    fun map(entity: FanboxCreatorPlanListStrictEntity): List<FanboxCreatorPlan> {
        return entity.body.map(creatorMapper::map)
    }

    fun map(entity: FanboxPaidRecordListEntity): List<FanboxPaidRecord> {
        return entity.body.payments.map {
            FanboxPaidRecord(
                id = it.id,
                paidAmount = it.paidAmount,
                paymentDateTime = Instant.parse(it.paymentDatetime),
                paymentMethod = FanboxPaymentMethod.fromString(it.paymentMethod),
                creator = creatorMapper.map(it.creator),
            )
        }
    }

    fun map(entity: FanboxNewsLettersEntity): List<FanboxNewsLetter> {
        return entity.body.map {
            FanboxNewsLetter(
                id = FanboxNewsLetterId(it.id),
                body = it.body,
                createdAt = Instant.parse(it.createdAt),
                creator = creatorMapper.map(it.creator),
                isRead = it.isRead,
            )
        }
    }

    fun map(entity: FanboxBellListEntity, endpoint: String): FanboxTolerantResult<PageNumberInfo<FanboxBell>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.items,
            deserializer = FanboxBellListEntity.Body.Item.serializer(),
        ) { item, _ ->
            val bell: FanboxBell = when (item.type) {
                "on_post_published" -> {
                    val post = requireNotNull(item.post) { "post is required for on_post_published" }
                    FanboxBell.PostPublished(
                        id = FanboxPostId(post.id),
                        notifiedDatetime = Instant.parse(item.notifiedDatetime),
                        post = postMapper.map(post),
                    )
                }

                "post_comment" -> {
                    FanboxBell.Comment(
                        id = FanboxCommentId(item.id),
                        notifiedDatetime = Instant.parse(item.notifiedDatetime),
                        comment = requireNotNull(item.postCommentBody) { "postCommentBody is required for post_comment" },
                        isRootComment = requireNotNull(item.isRootComment) { "isRootComment is required for post_comment" },
                        creatorId = FanboxCreatorId(requireNotNull(item.creatorId) { "creatorId is required for post_comment" }),
                        postId = FanboxPostId(requireNotNull(item.postId) { "postId is required for post_comment" }),
                        postTitle = requireNotNull(item.postTitle) { "postTitle is required for post_comment" },
                        userName = requireNotNull(item.userName) { "userName is required for post_comment" },
                        userProfileIconUrl = requireNotNull(item.userProfileImg) { "userProfileImg is required for post_comment" },
                    )
                }

                "post_comment_like" -> {
                    FanboxBell.Like(
                        id = item.id,
                        notifiedDatetime = Instant.parse(item.notifiedDatetime),
                        comment = requireNotNull(item.postCommentBody) { "postCommentBody is required for post_comment_like" },
                        creatorId = FanboxCreatorId(requireNotNull(item.creatorId) { "creatorId is required for post_comment_like" }),
                        postId = FanboxPostId(requireNotNull(item.postId) { "postId is required for post_comment_like" }),
                        count = requireNotNull(item.count) { "count is required for post_comment_like" },
                    )
                }

                else -> throw IllegalArgumentException("Unknown bell type")
            }
            FanboxTolerantResult(bell, emptyList())
        }
        return FanboxTolerantResult(
            value = PageNumberInfo(
                contents = decoded.value,
                nextPage = entity.body.nextUrl?.let { Url(it).parameters["page"]?.toIntOrNull() },
            ),
            mismatches = decoded.mismatches,
        )
    }

    fun map(entity: FanboxMetaDataEntity): FanboxMetaData {
        return with(entity) {
            FanboxMetaData(
                apiUrl = apiUrl,
                csrfToken = csrfToken,
                context = context?.let {
                    FanboxMetaData.Context(
                        privacyPolicy = FanboxMetaData.Context.PrivacyPolicy(
                            policyUrl = context.privacyPolicy.policyUrl,
                            revisionHistoryUrl = context.privacyPolicy.revisionHistoryUrl,
                            shouldShowNotice = context.privacyPolicy.shouldShowNotice,
                            updateDate = context.privacyPolicy.updateDate,
                        ),
                        user = FanboxMetaData.Context.User(
                            creatorId = context.user.creatorId?.let { id -> FanboxCreatorId(id) },
                            fanboxUserStatus = context.user.fanboxUserStatus,
                            hasAdultContent = context.user.hasAdultContent ?: false,
                            hasUnpaidPayments = context.user.hasUnpaidPayments,
                            iconUrl = context.user.iconUrl,
                            isCreator = context.user.isCreator,
                            isMailAddressOutdated = context.user.isMailAddressOutdated,
                            isSupporter = context.user.isSupporter,
                            lang = context.user.lang,
                            name = context.user.name,
                            planCount = context.user.planCount,
                            showAdultContent = context.user.showAdultContent,
                            userId = context.user.userId?.let { id -> FanboxUserId(id.toLong()) },
                        ),
                    )
                },
            )
        }
    }
}
