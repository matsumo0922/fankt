package me.matsumo.fankt.fanbox.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostItemsEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostsPaginateEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostSearchEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCover
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.domain.translateToCursor

internal class FanboxPostMapper {

    fun map(entity: FanboxPostListEntity): PageCursorInfo<FanboxPost> {
        return PageCursorInfo(
            contents = entity.body.items.map { map(it) },
            cursor = entity.body.nextUrl?.translateToCursor(),
        )
    }

    fun map(entity: FanboxCreatorPostItemsEntity, nextCursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return PageCursorInfo(
            contents = entity.body.map { map(it) },
            cursor = nextCursor,
        )
    }

    fun map(entity: FanboxPostEntity): FanboxPost {
        return FanboxPost(
            id = FanboxPostId(entity.id),
            title = entity.title,
            excerpt = entity.excerpt,
            publishedDatetime = Instant.parse(entity.publishedDatetime),
            updatedDatetime = Instant.parse(entity.updatedDatetime),
            isLiked = entity.isLiked,
            likeCount = entity.likeCount,
            commentCount = entity.commentCount,
            feeRequired = entity.feeRequired,
            isRestricted = entity.isRestricted,
            hasAdultContent = entity.hasAdultContent,
            tags = entity.tags,
            cover = entity.cover?.let { cover ->
                FanboxCover(
                    type = cover.type,
                    url = cover.url,
                )
            },
            user = entity.user?.let {
                FanboxUser(
                    userId = FanboxUserId(it.userId.toLong()),
                    creatorId = FanboxCreatorId(entity.creatorId),
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            },
        )
    }

    fun map(entity: FanboxPostDetailEntity): FanboxPostDetail {
        var bodyBlock: FanboxPostDetail.Body = FanboxPostDetail.Body.Unknown

        if (!entity.body.body?.blocks.isNullOrEmpty()) {
            entity.body.body?.blocks?.let { blocks ->
                // 文字列や画像、ファイルなどのブロックが混在している場合

                val images = entity.body.body.imageMap
                val files = entity.body.body.fileMap
                val urls = entity.body.body.urlEmbedMap

                bodyBlock = FanboxPostDetail.Body.Article(
                    blocks = blocks.mapNotNull { block ->
                        when {
                            block.text != null -> {
                                if (block.text.isEmpty()) null else FanboxPostDetail.Body.Article.Block.Text(block.text)
                            }

                            block.imageId != null -> {
                                images[block.imageId]?.let { image ->
                                    FanboxPostDetail.Body.Article.Block.Image(
                                        FanboxPostDetail.ImageItem(
                                            id = FanboxPostItemId(image.id),
                                            postId = FanboxPostId(entity.body.id),
                                            extension = image.extension,
                                            originalUrl = image.originalUrl,
                                            thumbnailUrl = image.thumbnailUrl,
                                            aspectRatio = image.width.toFloat() / image.height.toFloat(),
                                        ),
                                    )
                                }
                            }

                            block.fileId != null -> {
                                files[block.fileId]?.let { file ->
                                    FanboxPostDetail.Body.Article.Block.File(
                                        FanboxPostDetail.FileItem(
                                            id = FanboxPostItemId(file.id),
                                            postId = FanboxPostId(entity.body.id),
                                            extension = file.extension,
                                            name = file.name,
                                            size = file.size,
                                            url = file.url,
                                        ),
                                    )
                                }
                            }

                            block.urlEmbedId != null -> {
                                urls[block.urlEmbedId]?.let { url ->
                                    FanboxPostDetail.Body.Article.Block.Link(
                                        html = url.html,
                                        post = url.postInfo?.let { map(it) },
                                    )
                                }
                            }

                            else -> {
                                Napier.w { "FanboxPostDetailEntity translate error: Unknown block type. $block" }
                                null
                            }
                        }
                    },
                )
            }
        }

        if (!entity.body.body?.images.isNullOrEmpty()) {
            entity.body.body?.images?.let { blocks ->
                // 画像のみのブロックの場合

                bodyBlock = FanboxPostDetail.Body.Image(
                    text = entity.body.body.text.orEmpty(),
                    images = blocks.map {
                        FanboxPostDetail.ImageItem(
                            id = FanboxPostItemId(it.id),
                            postId = FanboxPostId(entity.body.id),
                            extension = it.extension,
                            originalUrl = it.originalUrl,
                            thumbnailUrl = it.thumbnailUrl,
                            aspectRatio = it.width.toFloat() / it.height.toFloat(),
                        )
                    },
                )
            }
        }

        if (!entity.body.body?.files.isNullOrEmpty()) {
            entity.body.body?.files?.let { blocks ->
                // ファイルのみのブロックの場合

                bodyBlock = FanboxPostDetail.Body.File(
                    text = entity.body.body.text.orEmpty(),
                    files = blocks.map {
                        FanboxPostDetail.FileItem(
                            id = FanboxPostItemId(it.id),
                            postId = FanboxPostId(entity.body.id),
                            name = it.name,
                            extension = it.extension,
                            size = it.size,
                            url = it.url,
                        )
                    },
                )
            }
        }

        return FanboxPostDetail(
            id = FanboxPostId(entity.body.id),
            title = entity.body.title,
            publishedDatetime = Instant.parse(entity.body.publishedDatetime),
            updatedDatetime = Instant.parse(entity.body.updatedDatetime),
            isLiked = entity.body.isLiked,
            isBookmarked = false,
            likeCount = entity.body.likeCount,
            coverImageUrl = entity.body.coverImageUrl,
            commentCount = entity.body.commentCount,
            feeRequired = entity.body.feeRequired,
            isRestricted = entity.body.isRestricted,
            hasAdultContent = entity.body.hasAdultContent,
            tags = entity.body.tags,
            user = entity.body.user?.let {
                FanboxUser(
                    userId = FanboxUserId(it.userId.toLong()),
                    creatorId = FanboxCreatorId(entity.body.creatorId),
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            },
            body = bodyBlock,
            excerpt = entity.body.excerpt,
            nextPost = entity.body.nextPost?.let {
                FanboxPostDetail.OtherPost(
                    id = FanboxPostId(it.id),
                    title = it.title,
                    publishedDatetime = Instant.parse(it.publishedDatetime),
                )
            },
            prevPost = entity.body.prevPost?.let {
                FanboxPostDetail.OtherPost(
                    id = FanboxPostId(it.id),
                    title = it.title,
                    publishedDatetime = Instant.parse(it.publishedDatetime),
                )
            },
            imageForShare = entity.body.imageForShare,
        )
    }

    fun map(entity: FanboxPostCommentListEntity): PageOffsetInfo<FanboxComment> {
        return PageOffsetInfo(
            contents = entity.body.items.map { map(it) },
            offset = entity.body.nextUrl?.let { Url(it).parameters["offset"]?.toIntOrNull() },
        )
    }

    fun map(entity: FanboxCommentListEntity.Item): FanboxComment {
        return with(entity) {
            FanboxComment(
                body = body,
                createdDatetime = Instant.parse(createdDatetime),
                id = FanboxCommentId(id),
                isLiked = isLiked,
                isOwn = isOwn,
                likeCount = likeCount,
                parentCommentId = FanboxCommentId(parentCommentId),
                rootCommentId = FanboxCommentId(rootCommentId),
                replies = replies.map { map(it) }.sortedBy { it.createdDatetime },
                user = user?.let {
                    FanboxUser(
                        userId = FanboxUserId(it.userId.toLong()),
                        creatorId = null,
                        name = it.name,
                        iconUrl = it.iconUrl,
                    )
                },
            )
        }
    }

    fun map(entity: FanboxPostSearchEntity): PageNumberInfo<FanboxPost> {
        return PageNumberInfo(
            contents = entity.body.items.map { map(it) },
            nextPage = entity.body.nextUrl?.let { Url(it).parameters["page"]?.toIntOrNull() },
        )
    }

    fun map(entity: FanboxCreatorPostsPaginateEntity): List<FanboxCursor> {
        return entity.body.map { it.translateToCursor() }
    }
}
