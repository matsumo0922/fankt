package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class FanboxPostDetailEntity(
    @SerialName("body")
    val body: ResponseBody,
) {
    @Serializable
    data class ResponseBody(
        @SerialName("post")
        val post: Body,
    )

    @Serializable
    data class Body(
        @SerialName("body")
        val body: JsonElement?,
        @SerialName("commentCount")
        val commentCount: Int,
        @SerialName("creatorId")
        val creatorId: String,
        @SerialName("excerpt")
        val excerpt: String,
        @SerialName("feeRequired")
        val feeRequired: Int,
        @SerialName("hasAdultContent")
        val hasAdultContent: Boolean,
        @SerialName("id")
        val id: String,
        @SerialName("imageForShare")
        val imageForShare: String,
        @SerialName("isLiked")
        val isLiked: Boolean,
        @SerialName("isRestricted")
        val isRestricted: Boolean,
        @SerialName("likeCount")
        val likeCount: Int,
        @SerialName("nextPost")
        val nextPost: NextPost?,
        @SerialName("prevPost")
        val prevPost: NextPost?,
        @SerialName("publishedDatetime")
        val publishedDatetime: String,
        @SerialName("tags")
        val tags: List<String>,
        @SerialName("title")
        val title: String,
        @SerialName("type")
        val type: String,
        @SerialName("updatedDatetime")
        val updatedDatetime: String,
        @SerialName("coverImageUrl")
        val coverImageUrl: String?,
        @SerialName("user")
        val user: FanboxUserEntity?,
    ) {
        @Serializable
        data class PostBody(
            @SerialName("text")
            val text: String? = null,
            @SerialName("video")
            val video: Video? = null,
            @SerialName("html")
            val html: String? = null,
            @SerialName("blocks")
            val blocks: List<JsonObject> = emptyList(),
            @SerialName("embedMap")
            val embedMap: Map<String, JsonObject> = emptyMap(),
            @SerialName("fileMap")
            val fileMap: Map<String, File> = emptyMap(),
            @SerialName("imageMap")
            val imageMap: Map<String, Image> = emptyMap(),
            @SerialName("urlEmbedMap")
            val urlEmbedMap: Map<String, Url> = emptyMap(),
            @SerialName("images")
            val images: List<Image> = emptyList(),
            @SerialName("files")
            val files: List<File> = emptyList(),
        ) {
            @Serializable
            data class Block(
                @SerialName("type")
                val type: String,
                @SerialName("text")
                val text: String?,
                @SerialName("imageId")
                val imageId: String?,
                @SerialName("fileId")
                val fileId: String?,
                @SerialName("urlEmbedId")
                val urlEmbedId: String?,
                @SerialName("embedId")
                val embedId: String? = null,
            )

            @Serializable
            data class Embed(
                @SerialName("id")
                val id: String? = null,
                @SerialName("serviceProvider")
                val serviceProvider: String? = null,
                @SerialName("contentId")
                val contentId: String? = null,
                @SerialName("videoId")
                val videoId: String? = null,
            )

            @Serializable
            data class Video(
                @SerialName("serviceProvider")
                val serviceProvider: String? = null,
                @SerialName("videoId")
                val videoId: String? = null,
                @SerialName("contentId")
                val contentId: String? = null,
            )

            @Serializable
            data class File(
                @SerialName("extension")
                val extension: String,
                @SerialName("id")
                val id: String,
                @SerialName("name")
                val name: String,
                @SerialName("size")
                val size: Long,
                @SerialName("url")
                val url: String,
            )

            @Serializable
            data class Image(
                @SerialName("extension")
                val extension: String,
                @SerialName("height")
                val height: Int,
                @SerialName("id")
                val id: String,
                @SerialName("originalUrl")
                val originalUrl: String,
                @SerialName("thumbnailUrl")
                val thumbnailUrl: String,
                @SerialName("width")
                val width: Int,
            )

            @Serializable
            data class Url(
                @SerialName("id")
                val id: String,
                @SerialName("type")
                val type: String,
                @SerialName("html")
                val html: String?,
                @SerialName("postInfo")
                val postInfo: FanboxPostEntity?,
            )
        }

        @Serializable
        data class NextPost(
            @SerialName("id")
            val id: String,
            @SerialName("publishedDatetime")
            val publishedDatetime: String,
            @SerialName("title")
            val title: String,
        )
    }
}
