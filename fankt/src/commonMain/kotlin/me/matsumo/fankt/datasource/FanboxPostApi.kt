package me.matsumo.fankt.datasource

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.json.JsonObject
import me.matsumo.fankt.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.domain.entity.FanboxPostSearchEntity

internal interface FanboxPostApi {

    @GET("post.listHome")
    suspend fun getHomePosts(
        @Query("limit") loadSize: String,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String?,
        @Query("maxId") maxId: String?,
    ): FanboxPostListEntity

    @GET("post.listSupporting")
    suspend fun getSupportedPosts(
        @Query("limit") loadSize: String,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String?,
        @Query("maxId") maxId: String?
    ): FanboxPostListEntity

    @GET("post.listCreator")
    suspend fun getCreatorPosts(
        @Query("creatorId") creatorId: String,
        @Query("limit") loadSize: String,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String?,
        @Query("maxId") maxId: String?
    ): FanboxPostListEntity

    @GET("post.info")
    suspend fun getPostDetail(
        @Query("postId") postId: String
    ): FanboxPostDetailEntity

    @GET("post.listComments")
    suspend fun getPostComment(
        @Query("postId") postId: String,
        @Query("offset") offset: Int,
        @Query("limit") loadSize: String,
    ): FanboxPostCommentListEntity

    @GET("post.listTagged")
    suspend fun getPostFromQuery(
        @Query("tag") query: String,
        @Query("creatorId") creatorId: String?,
        @Query("page") page: Int = 0
    ): FanboxPostSearchEntity

    @Headers("Content-Type: application/json")
    @POST("post.likePost")
    suspend fun likePost(
        @Body body: JsonObject
    )

    @Headers("Content-Type: application/json")
    @POST("post.addComment")
    suspend fun addComment(
        @Body body: JsonObject
    )

    @Headers("Content-Type: application/json")
    @POST("post.deleteComment")
    suspend fun deleteComment(
        @Body body: JsonObject
    )
}