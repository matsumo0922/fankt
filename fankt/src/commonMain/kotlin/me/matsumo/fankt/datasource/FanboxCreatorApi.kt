package me.matsumo.fankt.datasource

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import me.matsumo.fankt.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.domain.entity.FanboxCreatorTagListEntity

internal interface FanboxCreatorApi {

    @GET("creator.get")
    suspend fun getCreatorDetail(
        @Query("creatorId") creatorId: String
    ): FanboxCreatorDetailEntity

    @GET("creator.listFollowing")
    suspend fun getFollowingCreators(): FanboxCreatorListEntity

    @GET("creator.listPixiv")
    suspend fun getFollowingPixivCreators(): FanboxCreatorListEntity

    @GET("creator.listRecommended")
    suspend fun getRecommendedCreators(
        @Query("limit") loadSize: String
    ): FanboxCreatorListEntity

    @GET("plan.listCreator")
    suspend fun getCreatorPlans(
        @Query("creatorId") creatorId: String
    ): FanboxCreatorPlanListEntity

    @GET("legacy/support/creator")
    suspend fun getCreatorPlanDetail(
        @Query("creatorId") creatorId: String
    ): FanboxCreatorPlanDetailEntity

    @GET("tag.getFeatured")
    suspend fun getCreatorTags(
        @Query("creatorId") creatorId: String
    ): FanboxCreatorTagListEntity

    @Headers("Content-Type: application/json")
    @POST("follow.create")
    @FormUrlEncoded
    suspend fun followCreator(
        @Field("creatorId") creatorId: String,
    )

    @Headers("Content-Type: application/json")
    @POST("follow.delete")
    @FormUrlEncoded
    suspend fun unfollowCreator(
        @Field("creatorId") creatorId: String,
    )
}