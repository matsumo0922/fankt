package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.datasource.FanboxSearchApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper

internal class FanboxSearchRepository(
    private val fanboxSearchApi: FanboxSearchApi,
    private val fanboxSearchMapper: FanboxSearchMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun searchCreators(query: String) = withContext(ioDispatcher) {
        fanboxSearchApi.getCreatorFromQuery(query).let {
            fanboxSearchMapper.map(it)
        }
    }

    suspend fun searchTags(query: String) = withContext(ioDispatcher) {
        fanboxSearchApi.getTagFromQuery(query).let {
            fanboxSearchMapper.map(it)
        }
    }
}
