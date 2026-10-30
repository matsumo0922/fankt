package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost
import me.matsumo.fankt.fanbox.reportedTo
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink

/** クリエイターに関する endpoint を guest で実行する。 */
internal class FanboxCreatorRepository(
    private val guestHost: FanboxGuestHost,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.creatorGet,
            build = { buildCreatorDetailRequest(creatorId.value) },
            parse = { response -> parseCreatorDetail(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getFollowingCreators() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.creatorListFollowing,
            build = { buildFollowingCreatorsRequest() },
            parse = { response -> parseFollowingCreators(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getFollowingPixivCreators() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.creatorListPixiv,
            build = { buildFollowingPixivCreatorsRequest() },
            parse = { response -> parseFollowingPixivCreators(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getRecommendedCreators() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.creatorListRecommended,
            build = { buildRecommendedCreatorsRequest() },
            parse = { response -> parseRecommendedCreators(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.planListCreator,
            build = { buildCreatorPlansRequest(creatorId.value) },
            parse = { response -> parseCreatorPlans(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.legacySupportCreator,
            build = { buildCreatorPlanDetailRequest(creatorId.value) },
            parse = { response -> parseCreatorPlanDetail(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getCreatorTags(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.tagGetFeatured,
            build = { buildCreatorTagsRequest(creatorId.value) },
            parse = { response -> parseCreatorTags(response.bodyText, response.statusCode) },
        )
    }

    suspend fun followCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.followCreate) { buildFollowCreatorRequest(userId.value) }
    }

    suspend fun unfollowCreator(userId: FanboxUserId) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.followDelete) { buildUnfollowCreatorRequest(userId.value) }
    }
}
