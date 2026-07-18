package me.matsumo.fankt.fanbox.repository

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import me.matsumo.fankt.fanbox.FanboxExceptionFactory
import me.matsumo.fankt.fanbox.datasource.FanboxSearchApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper

internal class FanboxSearchRepository(
    private val fanboxSearchApi: FanboxSearchApi,
    private val fanboxSearchMapper: FanboxSearchMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun searchCreators(query: String, page: Int) = withContext(ioDispatcher) {
        val entity = fanboxSearchApi.getCreatorFromQuery(query, page)
        try {
            fanboxSearchMapper.map(entity)
        } catch (failure: SerializationException) {
            throw FanboxExceptionFactory.schemaMismatch(
                statusCode = HttpStatusCode.OK.value,
                html = entity.body.creators.flatMap { it.profileItems }.toString(),
                endpoint = "creator.search",
                cause = failure,
            )
        }
    }

    suspend fun searchTags(query: String) = withContext(ioDispatcher) {
        fanboxSearchApi.getTagFromQuery(query).let {
            fanboxSearchMapper.map(it)
        }
    }
}
