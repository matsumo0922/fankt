package me.matsumo.fankt.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.FanboxCursor
import me.matsumo.fankt.domain.PageCursorInfo
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.PageOffsetInfo
import me.matsumo.fankt.domain.entity.FanboxCommentListEntity
import me.matsumo.fankt.domain.entity.FanboxCoverEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPostListEntity
import me.matsumo.fankt.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.domain.entity.FanboxPostEntity
import me.matsumo.fankt.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.domain.entity.FanboxPostSearchEntity
import me.matsumo.fankt.domain.model.FanboxComment
import me.matsumo.fankt.domain.model.FanboxCover
import me.matsumo.fankt.domain.model.FanboxPost
import me.matsumo.fankt.domain.model.FanboxPostDetail
import me.matsumo.fankt.domain.model.FanboxUser
import me.matsumo.fankt.domain.model.id.FanboxCommentId
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId
import me.matsumo.fankt.domain.translateToCursor

internal fun FanboxPostListEntity.translate(): PageCursorInfo<FanboxPost> {
    return PageCursorInfo(
        contents = body.items.map { it.translate() },
        cursor = body.nextUrl?.translateToCursor(),
    )
}

internal fun FanboxCreatorPostListEntity.translate(nextCursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
    return PageCursorInfo(
        contents = body.map { it.translate() },
        cursor = nextCursor,
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
        cover = cover?.translate(),
        user = user?.translate()
    )
}

internal fun FanboxCoverEntity.translate(): FanboxCover {
    return FanboxCover(
        type = type,
        url = url,
    )
}

internal fun FanboxPostDetailEntity.translate(): FanboxPostDetail {
    var bodyBlock: FanboxPostDetail.Body = FanboxPostDetail.Body.Unknown

    if (!body.body?.blocks.isNullOrEmpty()) {
        body.body?.blocks?.let { blocks ->
            // 文字列や画像、ファイルなどのブロックが混在している場合

            val images = body.body.imageMap
            val files = body.body.fileMap
            val urls = body.body.urlEmbedMap

            bodyBlock = FanboxPostDetail.Body.Article(
                blocks = blocks.mapNotNull {
                    when {
                        it.text != null -> {
                            if (it.text.isEmpty()) null else FanboxPostDetail.Body.Article.Block.Text(it.text)
                        }

                        it.imageId != null -> {
                            images[it.imageId]?.let { image ->
                                FanboxPostDetail.Body.Article.Block.Image(
                                    FanboxPostDetail.ImageItem(
                                        id = image.id,
                                        postId = FanboxPostId(body.id),
                                        extension = image.extension,
                                        originalUrl = image.originalUrl,
                                        thumbnailUrl = image.thumbnailUrl,
                                        aspectRatio = image.width.toFloat() / image.height.toFloat(),
                                    ),
                                )
                            }
                        }

                        it.fileId != null -> {
                            files[it.fileId]?.let { file ->
                                FanboxPostDetail.Body.Article.Block.File(
                                    FanboxPostDetail.FileItem(
                                        id = file.id,
                                        postId = FanboxPostId(body.id),
                                        extension = file.extension,
                                        name = file.name,
                                        size = file.size,
                                        url = file.url,
                                    ),
                                )
                            }
                        }

                        it.urlEmbedId != null -> {
                            urls[it.urlEmbedId]?.let { url ->
                                FanboxPostDetail.Body.Article.Block.Link(
                                    html = url.html,
                                    post = url.postInfo?.translate(),
                                )
                            }
                        }

                        else -> {
                            Napier.w { "FanboxPostDetailEntity translate error: Unknown block type. $it" }
                            null
                        }
                    }
                },
            )
        }
    }

    if (!body.body?.images.isNullOrEmpty()) {
        body.body?.images?.let { blocks ->
            // 画像のみのブロックの場合

            bodyBlock = FanboxPostDetail.Body.Image(
                text = body.body.text.orEmpty(),
                images = blocks.map {
                    FanboxPostDetail.ImageItem(
                        id = it.id,
                        postId = FanboxPostId(body.id),
                        extension = it.extension,
                        originalUrl = it.originalUrl,
                        thumbnailUrl = it.thumbnailUrl,
                        aspectRatio = it.width.toFloat() / it.height.toFloat(),
                    )
                },
            )
        }
    }

    if (!body.body?.files.isNullOrEmpty()) {
        body.body?.files?.let { blocks ->
            // ファイルのみのブロックの場合

            bodyBlock = FanboxPostDetail.Body.File(
                text = body.body.text.orEmpty(),
                files = blocks.map {
                    FanboxPostDetail.FileItem(
                        id = it.id,
                        postId = FanboxPostId(body.id),
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
        id = FanboxPostId(body.id),
        title = body.title,
        creatorId = FanboxCreatorId(body.creatorId),
        publishedDatetime = Instant.parse(body.publishedDatetime),
        updatedDatetime = Instant.parse(body.updatedDatetime),
        isLiked = body.isLiked,
        likeCount = body.likeCount,
        coverImageUrl = body.coverImageUrl,
        commentCount = body.commentCount,
        feeRequired = body.feeRequired,
        isRestricted = body.isRestricted,
        hasAdultContent = body.hasAdultContent,
        tags = body.tags,
        user = body.user?.translate(),
        body = bodyBlock,
        excerpt = body.excerpt,
        nextPost = body.nextPost?.let {
            FanboxPostDetail.OtherPost(
                id = FanboxPostId(it.id),
                title = it.title,
                publishedDatetime = Instant.parse(it.publishedDatetime),
            )
        },
        prevPost = body.prevPost?.let {
            FanboxPostDetail.OtherPost(
                id = FanboxPostId(it.id),
                title = it.title,
                publishedDatetime = Instant.parse(it.publishedDatetime),
            )
        },
        imageForShare = body.imageForShare,
    )
}

internal fun FanboxCommentListEntity.Item.translate(): FanboxComment {
    return FanboxComment(
        body = body,
        createdDatetime = Instant.parse(createdDatetime),
        id = FanboxCommentId(id),
        isLiked = isLiked,
        isOwn = isOwn,
        likeCount = likeCount,
        parentCommentId = FanboxCommentId(parentCommentId),
        rootCommentId = FanboxCommentId(rootCommentId),
        replies = replies.map { it.translate() }.sortedBy { it.createdDatetime },
        user = user?.translate(),
    )
}

internal fun FanboxPostSearchEntity.translate(): PageNumberInfo<FanboxPost> {
    return PageNumberInfo(
        contents = body.items.map { it.translate() },
        nextPage = body.nextUrl?.let { Url(it).parameters["page"]?.toIntOrNull() },
    )
}

internal fun FanboxPostCommentListEntity.translate(): PageOffsetInfo<FanboxComment> {
    return PageOffsetInfo(
        contents = body.items.map { it.translate() },
        offset = body.nextUrl?.let { Url(it).parameters["offset"]?.toIntOrNull() },
    )
}
