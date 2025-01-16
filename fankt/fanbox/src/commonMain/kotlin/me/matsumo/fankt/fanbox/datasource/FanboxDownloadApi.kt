package me.matsumo.fankt.fanbox.datasource

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.ReqBuilder
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpStatement

internal interface FanboxDownloadApi {

    @GET("images/post/{postId}/{imageId}.jpg")
    suspend fun downloadPostImage(
        @Path("postId") postId: String,
        @Path("imageId") imageId: String,
        @ReqBuilder builder: HttpRequestBuilder.() -> Unit
    ) : HttpStatement

    @GET("images/post/{postId}/w/1200/{imageId}.jpg")
    suspend fun downloadPostThumbnailImage(
        @Path("postId") postId: String,
        @Path("imageId") imageId: String,
        @ReqBuilder builder: HttpRequestBuilder.() -> Unit
    ) : HttpStatement
}
