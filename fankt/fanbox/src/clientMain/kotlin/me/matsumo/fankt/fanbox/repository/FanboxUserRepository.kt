package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor

internal class FanboxUserRepository(
    private val requestExecutor: FanboxRequestExecutor,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val includeRawFragment: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getSupportedPlans() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.supportingPlans())
        FanboxResponses.supportingPlansStrict(response.bodyText, response.statusCode)
    }

    suspend fun getSupportedPlansTolerant() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.supportingPlans())
        FanboxResponses.supportingPlansTolerant(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getPaidRecords() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.paidRecords())
        FanboxResponses.paidRecords(response.bodyText, response.statusCode)
    }

    suspend fun getUnpaidRecords() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.unpaidRecords())
        FanboxResponses.unpaidRecords(response.bodyText, response.statusCode)
    }

    suspend fun getNewsLetters() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.newsletters())
        FanboxResponses.newsletters(response.bodyText, response.statusCode)
    }

    suspend fun getBells(page: Int) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.bells(page))
        FanboxResponses.bells(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getMetadata() = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.homepage())
        FanboxResponses.homepage(response.bodyText, response.statusCode)
    }
}
