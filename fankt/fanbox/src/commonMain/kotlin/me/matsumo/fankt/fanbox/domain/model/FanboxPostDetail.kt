package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId

@Serializable
data class FanboxPostDetail(
    val id: FanboxPostId,
    val title: String,
    val body: Body,
    val coverImageUrl: String?,
    val commentCount: Int,
    val excerpt: String,
    val feeRequired: Int,
    val hasAdultContent: Boolean,
    val imageForShare: String,
    val isLiked: Boolean,
    var isBookmarked: Boolean,
    val isRestricted: Boolean,
    val likeCount: Int,
    val tags: List<String>,
    val updatedDatetime: Instant,
    val publishedDatetime: Instant,
    val nextPost: OtherPost?,
    val prevPost: OtherPost?,
    val user: FanboxUser?,
) {
    val browserUrl get() = "https://www.fanbox.cc/@${user?.creatorId}/posts/$id"

    @Serializable
    sealed interface Body {
        val imageItems
            get() = when (this) {
                is Article -> blocks.filterIsInstance<Article.Block.Image>().map {
                    it.item
                }

                is Image -> images
                is File -> files.mapNotNull {
                    it.asImageItem()
                }

                is Unknown -> emptyList()
            }

        val fileItems
            get() = when (this) {
                is Article -> blocks.filterIsInstance<Article.Block.File>()
                    .map {
                        it.item
                    }

                is Image -> emptyList()
                is File -> files
                is Unknown -> emptyList()
            }

        @Serializable
        data class Article(val blocks: List<Block>) : Body {
            @Serializable
            sealed interface Block {
                @Serializable
                data class Text(val text: String) : Block

                @Serializable
                data class Image(val item: ImageItem) : Block

                @Serializable
                data class File(val item: FileItem) : Block

                @Serializable
                data class Link(
                    val html: String?,
                    val post: FanboxPost?,
                ) : Block
            }
        }

        @Serializable
        data class Image(
            val text: String,
            val images: List<ImageItem>,
        ) : Body

        @Serializable
        data class File(
            val text: String,
            val files: List<FileItem>,
        ) : Body

        @Serializable
        data object Unknown : Body
    }

    @Serializable
    data class OtherPost(
        val id: FanboxPostId,
        val title: String,
        val publishedDatetime: Instant,
    )

    @Serializable
    data class ImageItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val extension: String,
        val originalUrl: String,
        val thumbnailUrl: String,
        val aspectRatio: Float,
    )

    @Serializable
    data class VideoItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val extension: String,
        val url: String,
    )

    @Serializable
    data class FileItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val name: String,
        val extension: String,
        val size: Long,
        val url: String,
    ) {
        fun asImageItem(): ImageItem? {
            return if (!extension.lowercase().contains(Regex("""(jpg|jpeg|png|gif)"""))) {
                null
            } else {
                ImageItem(
                    id = id,
                    postId = postId,
                    extension = extension,
                    originalUrl = url,
                    thumbnailUrl = url,
                    aspectRatio = 1f,
                )
            }
        }

        fun asVideoItem(): VideoItem? {
            return if (!extension.lowercase().contains(Regex("""(mp4|webm)"""))) {
                null
            } else {
                VideoItem(
                    id = id,
                    postId = postId,
                    extension = extension,
                    url = url,
                )
            }
        }
    }
}
