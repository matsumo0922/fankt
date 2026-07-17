package me.matsumo.fankt.fanbox.datasource.mapper

import io.github.aakira.napier.Napier
import io.ktor.http.Url
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.createFanboxJson
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
    private val formatter: Json = createFanboxJson(),
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
        val bodyBlock = mapBody(post)

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

    private fun mapBody(post: FanboxPostDetailEntity.Body): FanboxPostDetail.Body {
        return when (post.type) {
            "article" -> mapArticleBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))
            "image" -> mapImageBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))
            "file" -> mapFileBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))
            else -> FanboxPostDetail.Body.Unknown(
                type = post.type,
                rawBodyJson = post.body?.toString(),
            )
        }
    }

    private fun requireKnownBody(post: FanboxPostDetailEntity.Body) =
        post.body ?: throw SerializationException("post.body is null for known post type '${post.type}'")

    private fun mapArticleBody(
        post: FanboxPostDetailEntity.Body,
        body: FanboxPostDetailEntity.Body.PostBody,
    ): FanboxPostDetail.Body.Article {
        return FanboxPostDetail.Body.Article(
            blocks = body.blocks.mapNotNull { block ->
                when {
                    block.text != null -> {
                        if (block.text.isEmpty()) null else FanboxPostDetail.Body.Article.Block.Text(block.text)
                    }

                    block.imageId != null -> {
                        body.imageMap[block.imageId]?.let { image ->
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
                        body.fileMap[block.fileId]?.let { file ->
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
                        body.urlEmbedMap[block.urlEmbedId]?.let { url ->
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

    private fun mapImageBody(
        post: FanboxPostDetailEntity.Body,
        body: FanboxPostDetailEntity.Body.PostBody,
    ): FanboxPostDetail.Body.Image {
        return FanboxPostDetail.Body.Image(
            text = body.text.orEmpty(),
            images = body.images.map {
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

    private fun mapFileBody(
        post: FanboxPostDetailEntity.Body,
        body: FanboxPostDetailEntity.Body.PostBody,
    ): FanboxPostDetail.Body.File {
        return FanboxPostDetail.Body.File(
            text = body.text.orEmpty(),
            files = body.files.map {
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
