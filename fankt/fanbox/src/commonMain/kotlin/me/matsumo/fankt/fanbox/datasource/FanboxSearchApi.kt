package me.matsumo.fankt.fanbox.datasource

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxTagListEntity

internal interface FanboxSearchApi {

    @GET("creator.search")
    suspend fun getCreatorFromQuery(
        @Query("q") query: String,
        @Query("page") page: Int = 0,
    ): FanboxCreatorSearchListEntity

    @GET("tag.search")
    suspend fun getTagFromQuery(
        @Query("q") query: String,
    ): FanboxTagListEntity
}
