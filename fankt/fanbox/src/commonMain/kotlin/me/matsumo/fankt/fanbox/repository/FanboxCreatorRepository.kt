package me.matsumo.fankt.fanbox.repository

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.FanboxExceptionFactory
import me.matsumo.fankt.fanbox.datasource.FanboxCreatorApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

internal class FanboxCreatorRepository(
    private val fanboxCreatorApi: FanboxCreatorApi,
    private val fanboxCreatorApiWithoutContentNegotiation: FanboxCreatorApi,
    private val fanboxCreatorMapper: FanboxCreatorMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        val entity = fanboxCreatorApi.getCreatorDetail(creatorId.value)
        try {
            fanboxCreatorMapper.map(entity)
        } catch (failure: SerializationException) {
            throw FanboxExceptionFactory.schemaMismatch(
                statusCode = HttpStatusCode.OK.value,
                html = entity.body.profileItems.toString(),
                endpoint = "creator.get",
                cause = failure,
            )
        }
    }

    suspend fun getFollowingCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getFollowingCreators().let {
            fanboxCreatorMapper.map(it, "creator.listFollowing")
        }
    }

    suspend fun getFollowingPixivCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getFollowingPixivCreators().let {
            fanboxCreatorMapper.map(it, "creator.listPixiv")
        }
    }

    suspend fun getRecommendedCreators() = withContext(ioDispatcher) {
        fanboxCreatorApi.getRecommendedCreators(LOAD_SIZE).let {
            fanboxCreatorMapper.map(it, "creator.listRecommended")
        }
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxCreatorApi.getCreatorPlans(creatorId.value).let {
            fanboxCreatorMapper.map(it, "plan.listCreator")
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
                // creatorUserId must stay a JSON string; userId.value is a Long and would
                // otherwise serialize as a JSON number and break the follow.create wire shape.
                text = buildJsonObject { put("creatorUserId", userId.value.toString()) }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    suspend fun unfollowCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        fanboxCreatorApiWithoutContentNegotiation.unfollowCreator(
            TextContent(
                // See followCreator: creatorUserId must stay a JSON string.
                text = buildJsonObject { put("creatorUserId", userId.value.toString()) }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    companion object {
        private const val LOAD_SIZE = "20"
    }
}
