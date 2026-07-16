package me.matsumo.fankt.fanbox.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.FanboxTolerantResult
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

internal class FanboxPostMapper(
    private val listItemDecoder: FanboxListItemDecoder = FanboxListItemDecoder(),
) {

    fun map(entity: FanboxPostListEntity, endpoint: String): FanboxTolerantResult<PageCursorInfo<FanboxPost>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.items,
            deserializer = FanboxPostEntity.serializer(),
        ) { item, _ -> FanboxTolerantResult(map(item), emptyList()) }
        return FanboxTolerantResult(
            value = PageCursorInfo(
                contents = decoded.value,
                cursor = entity.body.nextUrl?.translateToCursor(),
            ),
            mismatches = decoded.mismatches,
        )
    }

    fun map(
        entity: FanboxCreatorPostItemsEntity,
        nextCursor: FanboxCursor?,
        endpoint: String,
    ): FanboxTolerantResult<PageCursorInfo<FanboxPost>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body,
            deserializer = FanboxPostEntity.serializer(),
        ) { item, _ -> FanboxTolerantResult(map(item), emptyList()) }
        return FanboxTolerantResult(
            value = PageCursorInfo(
                contents = decoded.value,
                cursor = nextCursor,
            ),
            mismatches = decoded.mismatches,
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
        val post = entity.body.post
        var bodyBlock: FanboxPostDetail.Body = FanboxPostDetail.Body.Unknown

        if (!post.body?.blocks.isNullOrEmpty()) {
            post.body?.blocks?.let { blocks ->
                // 文字列や画像、ファイルなどのブロックが混在している場合

                val images = post.body.imageMap
                val files = post.body.fileMap
                val urls = post.body.urlEmbedMap

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
                                            postId = FanboxPostId(post.id),
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
                                            postId = FanboxPostId(post.id),
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

        if (!post.body?.images.isNullOrEmpty()) {
            post.body?.images?.let { blocks ->
                // 画像のみのブロックの場合

                bodyBlock = FanboxPostDetail.Body.Image(
                    text = post.body.text.orEmpty(),
                    images = blocks.map {
                        FanboxPostDetail.ImageItem(
                            id = FanboxPostItemId(it.id),
                            postId = FanboxPostId(post.id),
                            extension = it.extension,
                            originalUrl = it.originalUrl,
                            thumbnailUrl = it.thumbnailUrl,
                            aspectRatio = it.width.toFloat() / it.height.toFloat(),
                        )
                    },
                )
            }
        }

        if (!post.body?.files.isNullOrEmpty()) {
            post.body?.files?.let { blocks ->
                // ファイルのみのブロックの場合

                bodyBlock = FanboxPostDetail.Body.File(
                    text = post.body.text.orEmpty(),
                    files = blocks.map {
                        FanboxPostDetail.FileItem(
                            id = FanboxPostItemId(it.id),
                            postId = FanboxPostId(post.id),
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
            id = FanboxPostId(post.id),
            title = post.title,
            publishedDatetime = Instant.parse(post.publishedDatetime),
            updatedDatetime = Instant.parse(post.updatedDatetime),
            isLiked = post.isLiked,
            isBookmarked = false,
            likeCount = post.likeCount,
            coverImageUrl = post.coverImageUrl,
            commentCount = post.commentCount,
            feeRequired = post.feeRequired,
            isRestricted = post.isRestricted,
            hasAdultContent = post.hasAdultContent,
            tags = post.tags,
            user = post.user?.let {
                FanboxUser(
                    userId = FanboxUserId(it.userId.toLong()),
                    creatorId = FanboxCreatorId(post.creatorId),
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            },
            body = bodyBlock,
            excerpt = post.excerpt,
            nextPost = post.nextPost?.let {
                FanboxPostDetail.OtherPost(
                    id = FanboxPostId(it.id),
                    title = it.title,
                    publishedDatetime = Instant.parse(it.publishedDatetime),
                )
            },
            prevPost = post.prevPost?.let {
                FanboxPostDetail.OtherPost(
                    id = FanboxPostId(it.id),
                    title = it.title,
                    publishedDatetime = Instant.parse(it.publishedDatetime),
                )
            },
            imageForShare = post.imageForShare,
        )
    }

    fun map(
        entity: FanboxPostCommentListEntity,
        endpoint: String,
    ): FanboxTolerantResult<PageOffsetInfo<FanboxComment>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.body.commentList.items,
            deserializer = FanboxCommentListEntity.Item.serializer(),
        ) { item, indexPath -> mapComment(item, endpoint, indexPath) }
        return FanboxTolerantResult(
            value = PageOffsetInfo(
                contents = decoded.value,
                offset = entity.body.commentList.nextUrl?.let { Url(it).parameters["offset"]?.toIntOrNull() },
            ),
            mismatches = decoded.mismatches,
        )
    }

    private fun mapComment(
        entity: FanboxCommentListEntity.Item,
        endpoint: String,
        indexPath: List<Int>,
    ): FanboxTolerantResult<FanboxComment> {
        val decodedReplies = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity.replies,
            deserializer = FanboxCommentListEntity.Item.serializer(),
            indexPrefix = indexPath,
        ) { reply, replyPath -> mapComment(reply, endpoint, replyPath) }
        return with(entity) {
            FanboxTolerantResult(
                value = FanboxComment(
                    body = body,
                    createdDatetime = Instant.parse(createdDatetime),
                    id = FanboxCommentId(id),
                    isLiked = isLiked,
                    isOwn = isOwn,
                    likeCount = likeCount,
                    parentCommentId = FanboxCommentId(parentCommentId),
                    rootCommentId = FanboxCommentId(rootCommentId),
                    replies = decodedReplies.value.sortedBy { it.createdDatetime },
                    user = user?.let {
                        FanboxUser(
                            userId = FanboxUserId(it.userId.toLong()),
                            creatorId = null,
                            name = it.name,
                            iconUrl = it.iconUrl,
                        )
                    },
                ),
                mismatches = decodedReplies.mismatches,
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
