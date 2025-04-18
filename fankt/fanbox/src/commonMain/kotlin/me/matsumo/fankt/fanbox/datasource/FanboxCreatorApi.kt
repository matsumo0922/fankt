package me.matsumo.fankt.fanbox.datasource

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import io.ktor.content.TextContent
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorTagListEntity

internal interface FanboxCreatorApi {

    @GET("creator.get")
    suspend fun getCreatorDetail(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorDetailEntity

    @GET("creator.listFollowing")
    suspend fun getFollowingCreators(): FanboxCreatorListEntity

    @GET("creator.listPixiv")
    suspend fun getFollowingPixivCreators(): FanboxCreatorListEntity

    @GET("creator.listRecommended")
    suspend fun getRecommendedCreators(
        @Query("limit") loadSize: String,
    ): FanboxCreatorListEntity

    @GET("plan.listCreator")
    suspend fun getCreatorPlans(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorPlanListEntity

    @GET("legacy/support/creator")
    suspend fun getCreatorPlanDetail(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorPlanDetailEntity

    @GET("tag.getFeatured")
    suspend fun getCreatorTags(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorTagListEntity

    @Headers("Content-Type: application/json")
    @POST("follow.create")
    suspend fun followCreator(
        @Body body: TextContent,
    )

    @Headers("Content-Type: application/json")
    @POST("follow.delete")
    suspend fun unfollowCreator(
        @Body body: TextContent,
    )
}
