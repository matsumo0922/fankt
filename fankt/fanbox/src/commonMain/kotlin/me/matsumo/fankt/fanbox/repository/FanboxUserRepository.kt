package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost
import me.matsumo.fankt.fanbox.reportedTo
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink

/** ログイン中のユーザーに関する endpoint を guest で実行する。 */
internal class FanboxUserRepository(
    private val guestHost: FanboxGuestHost,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getSupportedPlans() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.planListSupporting,
            build = { buildSupportingPlansRequest() },
            parse = { response -> parseSupportingPlansStrict(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getSupportedPlansTolerant() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.planListSupporting,
            build = { buildSupportingPlansRequest() },
            parse = { response -> parseSupportingPlansTolerant(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getPaidRecords() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.paymentListPaid,
            build = { buildPaidRecordsRequest() },
            parse = { response -> parsePaidRecords(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getUnpaidRecords() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.paymentListUnpaid,
            build = { buildUnpaidRecordsRequest() },
            parse = { response -> parseUnpaidRecords(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getNewsLetters() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.newsletterList,
            build = { buildNewslettersRequest() },
            parse = { response -> parseNewsletters(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getBells(page: Int, markNotificationsRead: Boolean) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.bellList,
            build = { buildBellsRequest(page, markNotificationsRead) },
            parse = { response -> parseBells(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getMetadata() = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.homepage,
            build = { buildHomepageRequest() },
            parse = { response -> parseHomepage(response.bodyText, response.statusCode) },
        )
    }
}
