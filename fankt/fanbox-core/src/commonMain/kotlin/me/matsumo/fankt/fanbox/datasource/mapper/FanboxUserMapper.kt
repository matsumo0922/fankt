package me.matsumo.fankt.fanbox.datasource.mapper

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
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData.Context.PrivacyPolicy
import me.matsumo.fankt.fanbox.domain.model.FanboxNewsLetter
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.response.FanboxUrlParts
import kotlin.time.Instant
import me.matsumo.fankt.fanbox.domain.entity.FanboxBellListEntity.Body.Item as BellItem
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity.Context as MetadataContextEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity.Context.PrivacyPolicy as PrivacyPolicyEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity.Context.User as MetadataUserEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData.Context as MetadataContext
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData.Context.User as MetadataUser

/** ユーザー・通知・支払いレスポンスを公開ドメインモデルへ変換する。 */
internal class FanboxUserMapper(
    private val postMapper: FanboxPostMapper,
    private val creatorMapper: FanboxCreatorMapper,
    private val listItemDecoder: FanboxListItemDecoder = FanboxListItemDecoder(),
) {
    fun map(entity: FanboxCreatorPlanListEntity, endpoint: String): FanboxTolerantResult<List<FanboxCreatorPlan>> = creatorMapper.map(entity, endpoint)

    fun map(entity: FanboxCreatorPlanListStrictEntity): List<FanboxCreatorPlan> = entity.body.plans.map(creatorMapper::map)

    fun map(entity: FanboxPaidRecordListEntity): List<FanboxPaidRecord> = entity.body.payments.map {
        FanboxPaidRecord(
            id = it.id,
            paidAmount = it.paidAmount,
            paymentDateTime = Instant.parse(it.paymentDatetime),
            paymentMethod = FanboxPaymentMethod.fromString(it.paymentMethod),
            creator = creatorMapper.map(it.creator),
        )
    }

    fun map(entity: FanboxNewsLettersEntity): List<FanboxNewsLetter> = entity.body.map {
        FanboxNewsLetter(
            id = FanboxNewsLetterId(it.id),
            body = it.body,
            createdAt = Instant.parse(it.createdAt),
            creator = creatorMapper.map(it.creator),
            isRead = it.isRead,
        )
    }

    fun map(entity: FanboxBellListEntity, endpoint: String): FanboxTolerantResult<PageNumberInfo<FanboxBell>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.items,
            deserializer = BellItem.serializer(),
        ) { item, _ ->
            FanboxTolerantResult(
                value = mapBell(item),
                mismatches = emptyList(),
            )
        }
        return FanboxTolerantResult(
            value = PageNumberInfo(
                contents = decoded.value,
                nextPage = entity.body.nextUrl?.let {
                    FanboxUrlParts
                        .parse(it)
                        .queryValue("page")
                        ?.toIntOrNull()
                },
            ),
            mismatches = decoded.mismatches,
        )
    }

    private fun mapBell(item: BellItem): FanboxBell = when (item.type) {
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
                creatorId = FanboxCreatorId(
                    requireNotNull(item.creatorId) { "creatorId is required for post_comment" },
                ),
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
                creatorId = FanboxCreatorId(
                    requireNotNull(item.creatorId) { "creatorId is required for post_comment_like" },
                ),
                postId = FanboxPostId(
                    requireNotNull(item.postId) { "postId is required for post_comment_like" },
                ),
                count = requireNotNull(item.count) { "count is required for post_comment_like" },
            )
        }

        else -> throw IllegalArgumentException("Unknown bell type")
    }

    fun map(entity: FanboxMetaDataEntity): FanboxMetaData = FanboxMetaData(
        apiUrl = entity.apiUrl,
        csrfToken = entity.csrfToken,
        context = entity.context?.let(::mapContext),
    )

    private fun mapContext(context: MetadataContextEntity): MetadataContext = MetadataContext(
        privacyPolicy = mapPrivacyPolicy(context.privacyPolicy),
        user = mapMetadataUser(context.user),
    )

    private fun mapPrivacyPolicy(entity: PrivacyPolicyEntity): PrivacyPolicy = PrivacyPolicy(
        policyUrl = entity.policyUrl,
        revisionHistoryUrl = entity.revisionHistoryUrl,
        shouldShowNotice = entity.shouldShowNotice,
        updateDate = entity.updateDate,
    )

    private fun mapMetadataUser(entity: MetadataUserEntity): MetadataUser = MetadataUser(
        creatorId = entity.creatorId?.let { id -> FanboxCreatorId(id) },
        fanboxUserStatus = entity.fanboxUserStatus,
        hasAdultContent = entity.hasAdultContent ?: false,
        hasUnpaidPayments = entity.hasUnpaidPayments,
        iconUrl = entity.iconUrl,
        isCreator = entity.isCreator,
        isMailAddressOutdated = entity.isMailAddressOutdated,
        isSupporter = entity.isSupporter,
        lang = entity.lang,
        name = entity.name,
        planCount = entity.planCount,
        showAdultContent = entity.showAdultContent,
        userId = entity.userId?.let { id -> FanboxUserId(id.toLong()) },
    )
}
