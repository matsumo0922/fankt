package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity.Body.PostBody
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostSearchEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCover
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Text.LinkSpan
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Text.StyleSpan
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.FileItem
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.ImageItem
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.OtherPost
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.domain.translateToCursor
import me.matsumo.fankt.fanbox.response.FanboxUrlParts
import kotlin.time.Instant
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity.Body as PostEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity.Body.PostBody.Block as BlockEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity.Body.PostBody.Block.Link as LinkEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity.Body.PostBody.Block.Style as StyleEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block as ArticleBlock
private val EMBED_SERVICE_PROVIDERS = setOf(
    "twitter",
    "youtube",
    "vimeo",
    "soundcloud",
    "google_forms",
    "fanbox",
)

/** 投稿レスポンスを公開ドメインモデルへ変換する。 */
internal class FanboxPostMapper(
    private val listItemDecoder: FanboxListItemDecoder = FanboxListItemDecoder(),
    private val formatter: Json = createFanboxJson(),
) {

    fun map(entity: FanboxPostListEntity, endpoint: String): FanboxTolerantResult<PageCursorInfo<FanboxPost>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity
                .body
                .items,
            deserializer = FanboxPostEntity.serializer(),
        ) { item, _ -> FanboxTolerantResult(map(item), emptyList()) }
        return FanboxTolerantResult(
            value = PageCursorInfo(
                contents = decoded.value,
                cursor = entity
                    .body
                    .nextUrl
                    ?.translateToCursor(),
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
            items = entity
                .body
                .posts,
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

    fun map(entity: FanboxPostEntity): FanboxPost = FanboxPost(
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
        cover = entity
            .cover
            ?.let { cover ->
                FanboxCover(
                    type = cover.type,
                    url = cover.url,
                )
            },
        user = entity
            .user
            ?.let {
                FanboxUser(
                    userId = FanboxUserId(
                        it
                            .userId
                            .toLong(),
                    ),
                    creatorId = FanboxCreatorId(entity.creatorId),
                    name = it.name,
                    iconUrl = it.iconUrl,
                )
            },
    )

    fun map(entity: FanboxPostDetailEntity): FanboxPostDetail {
        val post = entity
            .body
            .post
        val bodyBlock = mapBody(post)

        return FanboxPostDetail(
            id = FanboxPostId(post.id),
            title = post.title,
            publishedDatetime = Instant.parse(post.publishedDatetime),
            updatedDatetime = Instant.parse(post.updatedDatetime),
            isLiked = post.isLiked,
            likeCount = post.likeCount,
            coverImageUrl = post.coverImageUrl,
            commentCount = post.commentCount,
            feeRequired = post.feeRequired,
            isRestricted = post.isRestricted,
            hasAdultContent = post.hasAdultContent,
            tags = post.tags,
            user = post
                .user
                ?.let {
                    FanboxUser(
                        userId = FanboxUserId(
                            it
                                .userId
                                .toLong(),
                        ),
                        creatorId = FanboxCreatorId(post.creatorId),
                        name = it.name,
                        iconUrl = it.iconUrl,
                    )
                },
            body = bodyBlock,
            excerpt = post.excerpt,
            nextPost = post
                .nextPost
                ?.let {
                    OtherPost(
                        id = FanboxPostId(it.id),
                        title = it.title,
                        publishedDatetime = Instant.parse(it.publishedDatetime),
                    )
                },
            prevPost = post
                .prevPost
                ?.let {
                    OtherPost(
                        id = FanboxPostId(it.id),
                        title = it.title,
                        publishedDatetime = Instant.parse(it.publishedDatetime),
                    )
                },
            imageForShare = post.imageForShare,
        )
    }

    private fun mapBody(post: PostEntity): Body {
        return when (post.type) {
            "article" -> mapArticleBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))

            "image" -> mapImageBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))

            "file" -> mapFileBody(post, formatter.decodeFromJsonElement(requireKnownBody(post)))

            "text" -> mapLegacyBody(post) { body ->
                body
                    .text
                    ?.let { Body.Text(it) }
            }

            "video" -> mapLegacyBody(post) { body ->
                val video = body.video ?: return@mapLegacyBody null
                val serviceProvider = video.serviceProvider ?: return@mapLegacyBody null
                val videoId = video.videoId ?: video.contentId ?: return@mapLegacyBody null
                Body.Video(serviceProvider, videoId)
            }

            "entry" -> mapLegacyBody(post) { body ->
                body
                    .html
                    ?.let { Body.Html(it) }
            }

            else -> unknownBody(post)
        }
    }

    private fun mapLegacyBody(post: PostEntity, mapper: (PostBody) -> Body?): Body {
        val rawBody = post.body ?: return unknownBody(post)
        return try {
            mapper(formatter.decodeFromJsonElement(rawBody)) ?: unknownBody(post)
        } catch (_: SerializationException) {
            unknownBody(post)
        }
    }

    private fun unknownBody(post: PostEntity) = Body.Unknown(
        type = post.type,
        rawBodyJson = post
            .body
            ?.toString(),
    )

    private fun requireKnownBody(post: PostEntity) =
        post.body ?: throw SerializationException("post.body is null for known post type '${post.type}'")

    private fun mapArticleBody(post: PostEntity, body: PostBody): Body.Article = Body.Article(
        blocks = body
            .blocks
            .mapNotNull { rawBlock -> mapArticleBlock(post, body, rawBlock) },
    )

    private fun mapArticleBlock(
        post: PostEntity,
        body: PostBody,
        rawBlock: JsonObject,
    ): ArticleBlock? {
        val blockType = (rawBlock["type"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content

        return when (blockType) {
            "p", "header" -> mapArticleText(rawBlock, blockType == "header")
            "image" -> mapArticleImage(post, body, rawBlock)
            "file" -> mapArticleFile(post, body, rawBlock)
            "url_embed" -> mapArticleLink(body, rawBlock)
            "embed" -> mapEmbedBlock(body, decodeKnownBlock(rawBlock), rawBlock.toString())
            else -> ArticleBlock.Unknown(rawBlock.toString())
        }
    }

    private fun mapArticleText(rawBlock: JsonObject, isHeader: Boolean): ArticleBlock.Text? {
        val block = decodeTextBlock(rawBlock)
        val text = block.text ?: return null

        return ArticleBlock.Text(
            text = text,
            styles = block
                .styles
                .orEmpty()
                .mapNotNull(::mapStyleSpan),
            links = block
                .links
                .orEmpty()
                .mapNotNull(::mapLinkSpan),
            isHeader = isHeader,
        )
    }

    private fun mapStyleSpan(style: StyleEntity): StyleSpan? {
        val type = style.type ?: return null
        val offset = style.offset ?: return null
        val length = style.length ?: return null

        return StyleSpan(
            type = type,
            offset = offset,
            length = length,
        )
    }

    private fun mapLinkSpan(link: LinkEntity): LinkSpan? {
        val offset = link.offset ?: return null
        val length = link.length ?: return null
        val url = link.url ?: return null

        return LinkSpan(
            offset = offset,
            length = length,
            url = url,
        )
    }

    private fun mapArticleImage(
        post: PostEntity,
        body: PostBody,
        rawBlock: JsonObject,
    ): ArticleBlock {
        val block = decodeKnownBlock(rawBlock)
        return block
            .imageId
            ?.let { body.imageMap[it] }
            ?.let { image ->
                ArticleBlock.Image(
                    ImageItem(
                        id = FanboxPostItemId(image.id),
                        postId = FanboxPostId(post.id),
                        extension = image.extension,
                        originalUrl = image.originalUrl,
                        thumbnailUrl = image.thumbnailUrl,
                        aspectRatio = image
                            .width
                            .toFloat() / image
                            .height
                            .toFloat(),
                    ),
                )
            } ?: ArticleBlock.Unknown(rawBlock.toString())
    }

    private fun mapArticleFile(
        post: PostEntity,
        body: PostBody,
        rawBlock: JsonObject,
    ): ArticleBlock {
        val block = decodeKnownBlock(rawBlock)
        return block
            .fileId
            ?.let { body.fileMap[it] }
            ?.let { file ->
                ArticleBlock.File(
                    FileItem(
                        id = FanboxPostItemId(file.id),
                        postId = FanboxPostId(post.id),
                        extension = file.extension,
                        name = file.name,
                        size = file.size,
                        url = file.url,
                    ),
                )
            } ?: ArticleBlock.Unknown(rawBlock.toString())
    }

    private fun mapArticleLink(body: PostBody, rawBlock: JsonObject): ArticleBlock {
        val block = decodeKnownBlock(rawBlock)
        return block
            .urlEmbedId
            ?.let { body.urlEmbedMap[it] }
            ?.let { url ->
                ArticleBlock.Link(
                    html = url.html,
                    post = url
                        .postInfo
                        ?.let { map(it) },
                    type = url.type,
                    url = url.url,
                )
            } ?: ArticleBlock.Unknown(rawBlock.toString())
    }

    private fun decodeKnownBlock(rawBlock: JsonObject) =
        formatter.decodeFromJsonElement<BlockEntity>(rawBlock)

    private fun decodeTextBlock(rawBlock: JsonObject): BlockEntity = try {
        decodeKnownBlock(rawBlock)
    } catch (_: SerializationException) {
        decodeKnownBlock(JsonObject(rawBlock - "styles" - "links"))
    }

    private fun mapEmbedBlock(
        body: PostBody,
        block: BlockEntity,
        rawBlockJson: String,
    ): ArticleBlock {
        val rawEmbed = block
            .embedId
            ?.let { body.embedMap[it] }
            ?: return ArticleBlock.Unknown(rawBlockJson)
        val embed = formatter.decodeFromJsonElement<PostBody.Embed>(rawEmbed)
        val contentId = embed.videoId ?: embed.contentId
        val serviceProvider = embed.serviceProvider
        val hasSupportedProvider = serviceProvider != null && serviceProvider in EMBED_SERVICE_PROVIDERS
        return if (hasSupportedProvider && contentId != null) {
            ArticleBlock.Embed(
                serviceProvider = serviceProvider,
                contentId = contentId,
            )
        } else {
            ArticleBlock.Unknown(rawEmbed.toString())
        }
    }

    private fun mapImageBody(post: PostEntity, body: PostBody): Body.Image = Body.Image(
        text = body
            .text
            .orEmpty(),
        images = body
            .images
            .map {
                ImageItem(
                    id = FanboxPostItemId(it.id),
                    postId = FanboxPostId(post.id),
                    extension = it.extension,
                    originalUrl = it.originalUrl,
                    thumbnailUrl = it.thumbnailUrl,
                    aspectRatio = it
                        .width
                        .toFloat() / it
                        .height
                        .toFloat(),
                )
            },
    )

    private fun mapFileBody(post: PostEntity, body: PostBody): Body.File = Body.File(
        text = body
            .text
            .orEmpty(),
        files = body
            .files
            .map {
                FileItem(
                    id = FanboxPostItemId(it.id),
                    postId = FanboxPostId(post.id),
                    name = it.name,
                    extension = it.extension,
                    size = it.size,
                    url = it.url,
                )
            },
    )

    fun map(entity: FanboxPostCommentListEntity, endpoint: String): FanboxTolerantResult<PageOffsetInfo<FanboxComment>> {
        val decoded = listItemDecoder.decodeAndMap(
            endpoint = endpoint,
            items = entity
                .body
                .commentList
                .items,
            deserializer = FanboxCommentListEntity.Item.serializer(),
        ) { item, indexPath -> mapComment(item, endpoint, indexPath) }
        return FanboxTolerantResult(
            value = PageOffsetInfo(
                contents = decoded.value,
                offset = entity
                    .body
                    .commentList
                    .nextUrl
                    ?.let {
                        FanboxUrlParts
                            .parse(it)
                            .queryValue("offset")
                            ?.toIntOrNull()
                    },
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
                    replies = decodedReplies
                        .value
                        .sortedBy { it.createdDatetime },
                    user = user?.let {
                        FanboxUser(
                            userId = FanboxUserId(
                                it
                                    .userId
                                    .toLong(),
                            ),
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

    fun map(entity: FanboxPostSearchEntity): PageNumberInfo<FanboxPost> = PageNumberInfo(
        contents = entity
            .body
            .items
            .map { map(it) },
        nextPage = entity
            .body
            .nextUrl
            ?.let {
                FanboxUrlParts
                    .parse(it)
                    .queryValue("page")
                    ?.toIntOrNull()
            },
    )

    fun map(entity: FanboxCreatorPostsPaginateEntity): List<FanboxCursor> = entity
        .body
        .pageUrls
        .map {
            it.translateToCursor()
        }
}
