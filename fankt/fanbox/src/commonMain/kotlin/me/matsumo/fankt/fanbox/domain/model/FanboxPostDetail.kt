package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import kotlin.time.Instant

/** 本文と前後の記事を含む FANBOX の投稿詳細。 */
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
    val isRestricted: Boolean,
    val likeCount: Int,
    val tags: List<String>,
    val updatedDatetime: Instant,
    val publishedDatetime: Instant,
    val nextPost: OtherPost?,
    val prevPost: OtherPost?,
    val user: FanboxUser?,
) {
    val browserUrl get() = "https://www.fanbox.cc/@${user?.creatorId?.value}/posts/${id.value}"

    @Serializable
    sealed interface Body {
        val imageItems
            get() = when (this) {
                is Article ->
                    blocks.filterIsInstance<Block.Image>().map {
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
                is Article -> blocks.filterIsInstance<Block.File>().map {
                    it.item
                }

                is Image -> emptyList()

                is File -> files

                is Text, is Video, is Html, is Unknown -> emptyList()
            }

        /** ブロックの列として表現した記事本文。 */
        @Serializable
        data class Article(val blocks: List<Block>) : Body {
            @Serializable
            sealed interface Block {
                /**
                 * 記事本文の段落または見出し。
                 * [isHeader] で段落と見出しを区別する。
                 * 範囲の座標は FANBOX の UTF-16 code unit に基づく未検証の値のため、適用前に [text] の境界と照合する。
                 */
                @Serializable
                data class Text(
                    val text: String,
                    val styles: List<StyleSpan> = emptyList(),
                    val links: List<LinkSpan> = emptyList(),
                    val isHeader: Boolean = false,
                ) : Block {
                    /**
                     * FANBOX の UTF-16 code unit 座標による未検証の装飾範囲。
                     * 適用前に [offset] と [length] を所属する Text の本文の境界と照合する。
                     * 未知の [type] も保持する。
                     */
                    @Serializable
                    data class StyleSpan(
                        val type: String,
                        val offset: Int,
                        val length: Int,
                    )

                    /**
                     * FANBOX の UTF-16 code unit 座標による未検証のリンク範囲。
                     * 適用前に [offset] と [length] を所属する Text の本文の境界と照合する。
                     * 遷移前に [url] をアプリの URL・scheme ポリシーで検証する。
                     */
                    @Serializable
                    data class LinkSpan(
                        val offset: Int,
                        val length: Int,
                        val url: String,
                    )
                }

                /** 投稿本文の画像情報。 */
                @Serializable
                data class Image(val item: ImageItem) : Block

                /** 投稿本文の添付ファイル情報。 */
                @Serializable
                data class File(val item: FileItem) : Block

                /**
                 * 記事ブロックから参照する URL の埋め込み。
                 * [type] は FANBOX の埋め込み種別で、`default` は [url]、`html` と `html.card` は未検証の [html]、`fanbox.post` は [post] を持つ。
                 * 未知の種別も保持し、種別固有のメタデータを推測して補完しない。
                 * 表示前に [html] を呼び出し側でサニタイズする。
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
                 * 外部サービスが提供する記事の埋め込み。
                 * fanbox の [url] はリダイレクト元のため、リダイレクト解決の通信は呼び出し側で行う。
                 */
                @Serializable
                data class Embed(val serviceProvider: String, val contentId: String) : Block {
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
                 * 既知の種別で表現できない記事ブロック。
                 * [rawJson] は元のブロックの JSON、またはフォールバックの原因になった参照先 map の要素の JSON を保持する。
                 */
                @Serializable
                data class Unknown(val rawJson: String) : Block
            }
        }

        /** 投稿本文の画像情報。 */
        @Serializable
        data class Image(val text: String, val images: List<ImageItem>) : Body

        /** 投稿本文の添付ファイル情報。 */
        @Serializable
        data class File(val text: String, val files: List<FileItem>) : Body

        /** プレーンテキストの投稿本文。 */
        @Serializable
        data class Text(val text: String) : Body

        /** 配信サービスと動画 ID を持つ投稿本文。 */
        @Serializable
        data class Video(val serviceProvider: String, val videoId: String) : Body {
            val url: String?
                get() = when (serviceProvider) {
                    "youtube" -> "https://www.youtube.com/watch?v=$videoId"
                    "vimeo" -> "https://vimeo.com/$videoId"
                    else -> null
                }
        }

        /**
         * FANBOX から受信した未サニタイズの HTML。
         * [html] は未検証のため、表示前に呼び出し側でサニタイズする。
         */
        @Serializable
        data class Html(val html: String) : Body

        /** 既知の形式へ変換できない投稿本文。 */
        @Serializable
        data class Unknown(
            @SerialName("postType")
            val type: String = "unknown",
            val rawBodyJson: String? = null,
        ) : Body
    }

    /** 前後の記事への参照。 */
    @Serializable
    data class OtherPost(
        val id: FanboxPostId,
        val title: String,
        val publishedDatetime: Instant,
    )

    /** 投稿に添付された画像。 */
    @Serializable
    data class ImageItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val extension: String,
        val originalUrl: String,
        val thumbnailUrl: String,
        val aspectRatio: Float,
    )

    /** 投稿に添付された動画。 */
    @Serializable
    data class VideoItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val extension: String,
        val url: String,
    )

    /** 投稿に添付されたファイル。 */
    @Serializable
    data class FileItem(
        val id: FanboxPostItemId,
        val postId: FanboxPostId,
        val name: String,
        val extension: String,
        val size: Long,
        val url: String,
    ) {
        fun asImageItem(): ImageItem? = if (!extension.lowercase().contains(Regex("""(jpg|jpeg|png|gif)"""))) {
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

        fun asVideoItem(): VideoItem? = if (!extension.lowercase().contains(Regex("""(mp4|webm)"""))) {
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
