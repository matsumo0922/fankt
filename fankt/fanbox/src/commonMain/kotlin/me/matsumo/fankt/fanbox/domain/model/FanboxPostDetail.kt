@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import kotlin.time.Instant

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

                is Text, is Video, is Html, is Unknown -> emptyList()
            }

        val fileItems
            get() = when (this) {
                is Article -> blocks.filterIsInstance<Article.Block.File>()
                    .map {
                        it.item
                    }

                is Image -> emptyList()
                is File -> files
                is Text, is Video, is Html, is Unknown -> emptyList()
            }

        @Serializable
        data class Article(val blocks: List<Block>) : Body {
            @Serializable
            sealed interface Block {
                /**
                 * A paragraph or heading from an article body.
                 *
                 * Use [isHeader] to select paragraph or heading presentation. Span offsets and
                 * lengths use the FANBOX payload's UTF-16 code-unit coordinates and are untrusted:
                 * callers must validate every range against [text] before applying it.
                 */
                @Serializable
                data class Text(
                    val text: String,
                    val styles: List<StyleSpan> = emptyList(),
                    val links: List<LinkSpan> = emptyList(),
                    val isHeader: Boolean = false,
                ) : Block {
                    /**
                     * An untrusted style range in FANBOX UTF-16 code-unit coordinates.
                     *
                     * Callers must verify [offset] and [length] against the owning Text's text
                     * boundary before applying the style. Unknown [type] values are preserved.
                     */
                    @Serializable
                    data class StyleSpan(
                        val type: String,
                        val offset: Int,
                        val length: Int,
                    )

                    /**
                     * An untrusted inline-link range in FANBOX UTF-16 code-unit coordinates.
                     *
                     * Callers must verify [offset] and [length] against the owning Text's text
                     * boundary and validate [url] against an application URL and scheme policy
                     * before navigation.
                     */
                    @Serializable
                    data class LinkSpan(
                        val offset: Int,
                        val length: Int,
                        val url: String,
                    )
                }

                @Serializable
                data class Image(val item: ImageItem) : Block

                @Serializable
                data class File(val item: FileItem) : Block

                /**
                 * A URL embed referenced by an article block.
                 *
                 * [type] is the FANBOX embed type. `default` represents a plain [url], `html`
                 * and `html.card` expose untrusted [html], and `fanbox.post` exposes [post].
                 * Unknown types are preserved without inventing type-specific metadata.
                 * Callers must sanitize [html] before rendering it.
                 */
                @Serializable
                data class Link(
                    val html: String?,
                    val post: FanboxPost?,
                    @SerialName("linkType")
                    val type: String = "unknown",
                    val url: String? = null,
                ) : Block

                /**
                 * A provider-backed article embed.
                 *
                 * For fanbox, [url] is the redirect source URL. Resolving the redirect is the
                 * caller's network responsibility.
                 */
                @Serializable
                data class Embed(
                    val serviceProvider: String,
                    val contentId: String,
                ) : Block {
                    val url: String?
                        get() = when (serviceProvider) {
                            "twitter" -> "https://twitter.com/_/status/$contentId"
                            "youtube" -> "https://www.youtube.com/watch?v=$contentId"
                            "vimeo" -> "https://vimeo.com/$contentId"
                            "soundcloud" -> "https://soundcloud.com/$contentId"
                            "google_forms" -> {
                                "https://docs.google.com/forms/d/e/$contentId/viewform?usp=sf_link"
                            }

                            "fanbox" -> "https://www.pixiv.net/fanbox/$contentId"
                            else -> null
                        }
                }

                /**
                 * An article block that cannot be represented by a known variant.
                 *
                 * [rawJson] contains the source block JSON, or the referenced map entry JSON when
                 * that entry caused the fallback.
                 */
                @Serializable
                data class Unknown(val rawJson: String) : Block
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
        data class Text(val text: String) : Body

        @Serializable
        data class Video(
            val serviceProvider: String,
            val videoId: String,
        ) : Body {
            val url: String?
                get() = when (serviceProvider) {
                    "youtube" -> "https://www.youtube.com/watch?v=$videoId"
                    "vimeo" -> "https://vimeo.com/$videoId"
                    else -> null
                }
        }

        /**
         * HTML received from FANBOX without sanitization.
         *
         * [html] is untrusted and must be sanitized by the caller before rendering.
         */
        @Serializable
        data class Html(val html: String) : Body

        @Serializable
        data class Unknown(
            @SerialName("postType")
            val type: String = "unknown",
            val rawBodyJson: String? = null,
        ) : Body
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
