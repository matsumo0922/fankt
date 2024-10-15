package me.matsumo.fankt.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.domain.entity.FanboxUserEntity
import me.matsumo.fankt.domain.model.FanboxBell
import me.matsumo.fankt.domain.model.FanboxCreator
import me.matsumo.fankt.domain.model.FanboxNewsLetter
import me.matsumo.fankt.domain.model.FanboxPaidRecord
import me.matsumo.fankt.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.domain.model.FanboxUser
import me.matsumo.fankt.domain.model.id.FanboxCommentId
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.domain.model.id.FanboxPostId

internal fun FanboxUserEntity.translate(): FanboxUser {
    return FanboxUser(
        userId = userId,
        name = name,
        iconUrl = iconUrl,
    )
}

internal fun FanboxPaidRecordListEntity.translate(): List<FanboxPaidRecord> {
    return body.map {
        FanboxPaidRecord(
            id = it.id,
            paidAmount = it.paidAmount,
            paymentDateTime = Instant.parse(it.paymentDatetime),
            paymentMethod = FanboxPaymentMethod.fromString(it.paymentMethod),
            creator = it.creator.translate(),
        )
    }
}

internal fun FanboxNewsLettersEntity.translate(): List<FanboxNewsLetter> {
    return body.map {
        FanboxNewsLetter(
            body = it.body,
            createdAt = Instant.parse(it.createdAt),
            creator = it.creator.translate(),
            id = FanboxNewsLetterId(it.id),
            isRead = it.isRead,
        )
    }
}

internal fun FanboxBellListEntity.translate(): PageNumberInfo<FanboxBell> {
    return PageNumberInfo(
        contents = body.items.mapNotNull {
            when (it.type) {
                "on_post_published" -> {
                    FanboxBell.PostPublished(
                        id = FanboxPostId(it.post!!.id),
                        notifiedDatetime = Instant.parse(it.notifiedDatetime),
                        post = it.post.translate(),
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
        nextPage = body.nextUrl?.let { Url(it).parameters["page"]?.toIntOrNull() },
    )
}