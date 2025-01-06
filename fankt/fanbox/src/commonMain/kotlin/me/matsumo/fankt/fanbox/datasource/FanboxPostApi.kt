package me.matsumo.fankt.fanbox.datasource

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import io.ktor.content.TextContent
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostItemsEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostsPaginateEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostSearchEntity

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
        @Query("maxId") maxId: String?,
    ): FanboxPostListEntity

    @GET("post.listCreator")
    suspend fun getCreatorPosts(
        @Query("creatorId") creatorId: String,
        @Query("limit") loadSize: String,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String?,
        @Query("maxId") maxId: String?,
    ): FanboxCreatorPostItemsEntity

    @GET("post.paginateCreator")
    suspend fun getCreatorPostsPagination(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorPostsPaginateEntity

    @GET("post.info")
    suspend fun getPostDetail(
        @Query("postId") postId: String,
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
        @Query("page") page: Int = 0,
    ): FanboxPostSearchEntity

    @Headers("Content-Type: application/json")
    @POST("post.likePost")
    suspend fun likePost(
        @Body body: TextContent,
    )

    @Headers("Content-Type: application/json")
    @POST("post.addComment")
    suspend fun addComment(
        @Body body: TextContent,
    )

    @Headers("Content-Type: application/json")
    @POST("post.deleteComment")
    suspend fun deleteComment(
        @Body body: TextContent,
    )
}
