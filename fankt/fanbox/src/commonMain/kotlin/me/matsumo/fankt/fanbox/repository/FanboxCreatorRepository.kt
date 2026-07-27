package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor

internal class FanboxCreatorRepository(
    private val requestExecutor: FanboxRequestExecutor,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val includeRawFragment: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.creatorDetail(creatorId))
        FanboxResponses.creatorDetail(response.bodyText, response.statusCode)
    }

    suspend fun getFollowingCreators() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.followingCreators())
        FanboxResponses.followingCreators(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getFollowingPixivCreators() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.followingPixivCreators())
        FanboxResponses.followingPixivCreators(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getRecommendedCreators() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.recommendedCreators())
        FanboxResponses.recommendedCreators(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.creatorPlans(creatorId))
        FanboxResponses.creatorPlans(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.creatorPlanDetail(creatorId))
        FanboxResponses.creatorPlanDetail(response.bodyText, response.statusCode)
    }

    suspend fun getCreatorTags(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.creatorTags(creatorId))
        FanboxResponses.creatorTags(response.bodyText, response.statusCode)
    }

    suspend fun followCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.followCreator(userId))
        FanboxResponses.followCreator(response.bodyText)
    }

    suspend fun unfollowCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.unfollowCreator(userId))
        FanboxResponses.unfollowCreator(response.bodyText)
    }
}
