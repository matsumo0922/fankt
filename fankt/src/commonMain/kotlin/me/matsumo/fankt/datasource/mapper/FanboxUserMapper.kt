package me.matsumo.fankt.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.domain.model.FanboxBell
import me.matsumo.fankt.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.domain.model.FanboxNewsLetter
import me.matsumo.fankt.domain.model.FanboxPaidRecord
import me.matsumo.fankt.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.domain.model.id.FanboxCommentId
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.domain.model.id.FanboxPostId

internal class FanboxUserMapper(
    private val postMapper: FanboxPostMapper,
    private val creatorMapper: FanboxCreatorMapper,
) {
    fun map(entity: FanboxCreatorPlanListEntity): List<FanboxCreatorPlan> {
        return creatorMapper.map(entity)
    }

    fun map(entity: FanboxPaidRecordListEntity): List<FanboxPaidRecord> {
        return entity.body.map {
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

    fun map(entity: FanboxBellListEntity): PageNumberInfo<FanboxBell> {
        return PageNumberInfo(
            contents = entity.body.items.mapNotNull {
                when (it.type) {
                    "on_post_published" -> {
                        FanboxBell.PostPublished(
                            id = FanboxPostId(it.post!!.id),
                            notifiedDatetime = Instant.parse(it.notifiedDatetime),
                            post = postMapper.map(it.post),
                        )
                    }

                    "post_comment" -> {
                        FanboxBell.Comment(
                            id = FanboxCommentId(it.id),
                            notifiedDatetime = Instant.parse(it.notifiedDatetime),
                            comment = it.postCommentBody!!,
                            isRootComment = it.isRootComment!!,
                            creatorId = FanboxCreatorId(it.creatorId!!),
                            postId = FanboxPostId(it.postId!!),
                            postTitle = it.postTitle!!,
                            userName = it.userName!!,
                            userProfileIconUrl = it.userProfileImg!!,
                        )
                    }

                    "post_comment_like" -> {
                        FanboxBell.Like(
                            id = it.id,
                            notifiedDatetime = Instant.parse(it.notifiedDatetime),
                            comment = it.postCommentBody!!,
                            creatorId = FanboxCreatorId(it.creatorId!!),
                            postId = FanboxPostId(it.postId!!),
                            count = it.count!!,
                        )
                    }

                    else -> {
                        Napier.w { "FanboxBellItemsEntity translate error: Unknown bell type. $it" }
                        null
                    }
                }
            },
            nextPage = entity.body.nextUrl?.let { Url(it).parameters["page"]?.toIntOrNull() },
        )
    }
}