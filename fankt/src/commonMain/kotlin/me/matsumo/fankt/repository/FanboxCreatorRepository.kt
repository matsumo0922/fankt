package me.matsumo.fankt.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.matsumo.fankt.datasource.FanboxCreatorApi
import me.matsumo.fankt.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.domain.model.id.FanboxCreatorId

internal class FanboxCreatorRepository(
    private val fanboxCreatorApi: FanboxCreatorApi,
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

    suspend fun getRecommendedCreators(loadSize: String) = withContext(ioDispatcher) {
        fanboxCreatorApi.getRecommendedCreators(loadSize).let {
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

    suspend fun followCreator(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.followCreator(
            body = JsonObject(mapOf("creatorId" to JsonPrimitive(creatorId.value)))
        )
    }

    suspend fun unfollowCreator(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.unfollowCreator(
            body = JsonObject(mapOf("creatorId" to JsonPrimitive(creatorId.value)))
        )
    }
}