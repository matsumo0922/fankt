package me.matsumo.fankt.fanbox.datasource

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import me.matsumo.fankt.fanbox.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity

internal interface FanboxUserApi {

    @GET("plan.listSupporting")
    suspend fun getSupportedPlans(): FanboxCreatorPlanListEntity

    @GET("payment.listPaid")
    suspend fun getPaidRecords(): FanboxPaidRecordListEntity

    @GET("payment.listUnpaid")
    suspend fun getUnpaidRecords(): FanboxPaidRecordListEntity

    @GET("newsletter.list")
    suspend fun getNewsLetters(): FanboxNewsLettersEntity

    @GET("bell.list")
    suspend fun getBells(
        @Query("page") page: Int = 0,
        @Query("skipConvertUnreadNotification") skipConvertUnreadNotification: Int = 0,
        @Query("commentOnly") commentOnly: Int = 0,
    ): FanboxBellListEntity

    @GET("https://www.fanbox.cc/")
    suspend fun getHomePage(): String
}
