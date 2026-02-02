package me.matsumo.fankt.fanbox.repository

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.FanboxUserApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity

internal class FanboxUserRepository(
    private val fanboxUserApi: FanboxUserApi,
    private val fanboxUserMapper: FanboxUserMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getSupportedPlans() = withContext(ioDispatcher) {
        fanboxUserApi.getSupportedPlans().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getPaidRecords() = withContext(ioDispatcher) {
        fanboxUserApi.getPaidRecords().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getUnpaidRecords() = withContext(ioDispatcher) {
        fanboxUserApi.getUnpaidRecords().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getNewsLetters() = withContext(ioDispatcher) {
        fanboxUserApi.getNewsLetters().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getBells(page: Int) = withContext(ioDispatcher) {
        fanboxUserApi.getBells(page).let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getMetadata(formatter: Json) = withContext(ioDispatcher) {
        fanboxUserApi.getHomePage().let {
            val doc = Ksoup.parse(it)
            val meta = doc.select("meta[name=metadata]").first()?.attr("content")
            val data = formatter.decodeFromString(FanboxMetaDataEntity.serializer(), meta!!)

            fanboxUserMapper.map(data)
        }
    }
}
