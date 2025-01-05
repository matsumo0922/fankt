package me.matsumo.fankt.repository

import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.datasource.FanboxCreatorApi
import me.matsumo.fankt.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxUserId

internal class FanboxCreatorRepository(
    private val fanboxCreatorApi: FanboxCreatorApi,
    private val fanboxCreatorApiWithoutContentNegotiation: FanboxCreatorApi,
    private val fanboxCreatorMapper: FanboxCreatorMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.getCreatorDetail(creatorId.value).let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getFollowingCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getFollowingCreators().let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getFollowingPixivCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getFollowingPixivCreators().let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getRecommendedCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getRecommendedCreators(LOAD_SIZE).let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.getCreatorPlans(creatorId.value).let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.getCreatorPlanDetail(creatorId.value).let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun getCreatorTags(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.getCreatorTags(creatorId.value).let {
            fanboxCreatorMapper.map(it)
        }
    }

    suspend fun followCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        fanboxCreatorApiWithoutContentNegotiation.followCreator(
            TextContent(
                text = buildJsonObject { put("creatorUserId", userId.toString()) }.toString(),
                contentType = ContentType.Application.Json
            )
        )
    }

    suspend fun unfollowCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        fanboxCreatorApiWithoutContentNegotiation.unfollowCreator(
            TextContent(
                text = buildJsonObject { put("creatorUserId", userId.toString()) }.toString(),
                contentType = ContentType.Application.Json
            )
        )
    }

    companion object {
        private const val LOAD_SIZE = "20"
    }
}
