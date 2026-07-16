package me.matsumo.fankt.fanbox.repository

import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.datasource.FanboxUserApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.datasource.parser.FanboxMetadataParser

internal class FanboxUserRepository(
    private val fanboxUserApi: FanboxUserApi,
    private val fanboxUserMapper: FanboxUserMapper,
    private val fanboxMetadataParser: FanboxMetadataParser,
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

    suspend fun getMetadata() = withContext(ioDispatcher) {
        fanboxUserApi.getHomePage().let { response ->
            val data = fanboxMetadataParser.parse(
                html = response.bodyAsText(),
                statusCode = response.status.value,
                endpoint = "homepage",
            )

            fanboxUserMapper.map(data)
        }
    }
}
