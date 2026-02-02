package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId

@Serializable
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
    ) : FanboxBell

    data class Like(
        val id: String,
        val notifiedDatetime: Instant,
        val comment: String,
        val creatorId: FanboxCreatorId,
        val postId: FanboxPostId,
        val count: Int,
    ) : FanboxBell

    data class PostPublished(
        val id: FanboxPostId,
        val notifiedDatetime: Instant,
        val post: FanboxPost,
    ) : FanboxBell
}
