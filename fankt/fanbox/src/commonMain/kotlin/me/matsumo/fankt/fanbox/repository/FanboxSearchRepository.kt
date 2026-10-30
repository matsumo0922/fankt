package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost

/** 検索に関する endpoint を guest で実行する。 */
internal class FanboxSearchRepository(
    private val guestHost: FanboxGuestHost,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun searchCreators(query: String, page: Int) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.creatorSearch,
            build = { buildSearchCreatorsRequest(query, page) },
            parse = { response -> parseSearchCreators(response.bodyText, response.statusCode) },
        )
    }

    suspend fun searchTags(query: String) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.tagSearch,
            build = { buildSearchTagsRequest(query) },
            parse = { response -> parseSearchTags(response.bodyText, response.statusCode) },
        )
    }
}
