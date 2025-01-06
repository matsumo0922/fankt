package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId

sealed interface FanboxBell {
    data class Comment(
        val id: FanboxCommentId,
        val notifiedDatetime: Instant,
        val comment: String,
        val isRootComment: Boolean,
        val creatorId: FanboxCreatorId,
        val postId: FanboxPostId,
        val postTitle: String,
        val userName: String,
        val userProfileIconUrl: String,
    ) : me.matsumo.fankt.fanbox.domain.model.FanboxBell

    data class Like(
        val id: String,
        val notifiedDatetime: Instant,
        val comment: String,
        val creatorId: FanboxCreatorId,
        val postId: FanboxPostId,
        val count: Int,
    ) : me.matsumo.fankt.fanbox.domain.model.FanboxBell

    data class PostPublished(
        val id: FanboxPostId,
        val notifiedDatetime: Instant,
        val post: me.matsumo.fankt.fanbox.domain.model.FanboxPost,
    ) : me.matsumo.fankt.fanbox.domain.model.FanboxBell
}
