package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId

data class FanboxPostDetail(
    val id: FanboxPostId,
    val title: String,
    val body: me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body,
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
    val nextPost: me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.OtherPost?,
    val prevPost: me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.OtherPost?,
    val user: me.matsumo.fankt.fanbox.domain.model.FanboxUser?,
) {
    val browserUrl get() = "https://www.fanbox.cc/@${user?.creatorId}/posts/$id"

    sealed interface Body {
        val imageItems
            get() = when (this) {
                is me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article -> blocks.filterIsInstance<me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Image>().map {
                    it.item
                }
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Image -> images
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.File -> files.mapNotNull {
                    it.asImageItem()
                }
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Unknown -> emptyList()
            }

        val fileItems
            get() = when (this) {
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article -> blocks.filterIsInstance<_root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.File>().map {
                    it.item
                }
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Image -> emptyList()
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.File -> files
                is _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Unknown -> emptyList()
            }

        data class Article(val blocks: List<_root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block>) :
            _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body {
            sealed interface Block {
                data class Text(val text: String) : _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block

                data class Image(val item: _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.ImageItem) :
                    _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block

                data class File(val item: _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.FileItem) :
                    _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block

                data class Link(
                    val html: String?,
                    val post: _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPost?,
                ) : _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block
            }
        }

        data class Image(
            val text: String,
            val images: List<_root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.ImageItem>,
        ) : _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body

        data class File(
            val text: String,
            val files: List<_root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.FileItem>,
        ) : _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body

        data object Unknown : _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body
    }

    data class OtherPost(
        val id: FanboxPostId,
        val title: String,
        val publishedDatetime: Instant,
    )

    data class ImageItem(
        val id: String,
        val postId: FanboxPostId,
        val extension: String,
        val originalUrl: String,
        val thumbnailUrl: String,
        val aspectRatio: Float,
    )

    data class VideoItem(
        val id: String,
        val postId: FanboxPostId,
        val extension: String,
        val url: String,
    )

    data class FileItem(
        val id: String,
        val postId: FanboxPostId,
        val name: String,
        val extension: String,
        val size: Long,
        val url: String,
    ) {
        fun asImageItem(): _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.ImageItem? {
            return if (!extension.lowercase().contains(Regex("""(jpg|jpeg|png|gif)"""))) {
                null
            } else {
                _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.ImageItem(
                    id = id,
                    postId = postId,
                    extension = extension,
                    originalUrl = url,
                    thumbnailUrl = url,
                    aspectRatio = 1f,
                )
            }
        }

        fun asVideoItem(): _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.VideoItem? {
            return if (!extension.lowercase().contains(Regex("""(mp4|webm)"""))) {
                null
            } else {
                _root_ide_package_.me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.VideoItem(
                    id = id,
                    postId = postId,
                    extension = extension,
                    url = url,
                )
            }
        }
    }
}
