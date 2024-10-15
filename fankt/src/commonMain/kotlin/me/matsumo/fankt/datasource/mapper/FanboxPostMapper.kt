package me.matsumo.fankt.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.PageCursorInfo
import me.matsumo.fankt.domain.entity.FanboxPostEntity
import me.matsumo.fankt.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.domain.model.FanboxCover
import me.matsumo.fankt.domain.model.FanboxPost
import me.matsumo.fankt.domain.model.FanboxUser
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId
import me.matsumo.fankt.domain.translateToCursor

internal fun FanboxPostListEntity.translate(): PageCursorInfo<FanboxPost> {
    return PageCursorInfo(
        contents = body.items.map { it.translate() },
        cursor = body.nextUrl?.translateToCursor(),
    )
}

internal fun FanboxPostEntity.translate(): FanboxPost {
    return FanboxPost(
        id = FanboxPostId(id),
        title = title,
        excerpt = excerpt,
        publishedDatetime = Instant.parse(publishedDatetime),
        updatedDatetime = Instant.parse(updatedDatetime),
        isLiked = isLiked,
        likeCount = likeCount,
        commentCount = commentCount,
        feeRequired = feeRequired,
        isRestricted = isRestricted,
        hasAdultContent = hasAdultContent,
        tags = tags,
        cover = cover?.let { cover ->
            FanboxCover(
                type = cover.type,
                url = cover.url,
            )
        },
        user = user?.let {
            FanboxUser(
                userId = it.userId,
                creatorId = FanboxCreatorId(creatorId),
                name = it.name,
                iconUrl = it.iconUrl,
            )
        }
    )
}