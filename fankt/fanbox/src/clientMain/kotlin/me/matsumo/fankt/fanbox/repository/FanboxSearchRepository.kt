package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor

internal class FanboxSearchRepository(
    private val requestExecutor: FanboxRequestExecutor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun searchCreators(query: String, page: Int) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.searchCreators(query, page))
        FanboxResponses.searchCreators(response.bodyText, response.statusCode)
    }

    suspend fun searchTags(query: String) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.searchTags(query))
        FanboxResponses.searchTags(response.bodyText, response.statusCode)
    }
}
