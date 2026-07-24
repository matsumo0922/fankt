@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter
import kotlin.time.Clock
import kotlin.time.Duration

internal object FanboxExceptionFactory {
    internal const val MAX_RAW_BODY_LENGTH = FanboxDiagnostics.MAX_FRAGMENT_LENGTH
    private const val DOWNLOAD_ENDPOINT = "download"

    suspend fun fromDownloadHttpResponse(
        response: HttpResponse,
        nowEpochMilliseconds: Long = Clock.System.now().toEpochMilliseconds(),
    ): FanboxException = FanboxFailureInterpreter.httpFailure(
        endpoint = DOWNLOAD_ENDPOINT,
        statusCode = response.status.value,
        rawBody = null,
        retryAfter = response.headers[HttpHeaders.RetryAfter],
        nowEpochMilliseconds = nowEpochMilliseconds,
    )

    fun schemaMismatch(
        statusCode: Int?,
        html: String,
        endpoint: String,
        cause: Throwable?,
    ): FanboxException.SchemaMismatch = FanboxFailureInterpreter.schemaMismatch(
        statusCode = statusCode,
        body = html,
        endpoint = endpoint,
        cause = cause,
    )

    fun downloadNetwork(cause: Throwable): FanboxException.Network =
        FanboxFailureInterpreter.network(DOWNLOAD_ENDPOINT, cause)

    fun sanitizeFragment(body: String): String = FanboxDiagnostics.sanitizeFragment(body)

    fun parseRetryAfter(value: String?, nowEpochMilliseconds: Long): Duration? =
        FanboxFailureInterpreter.parseRetryAfter(value, nowEpochMilliseconds)
}
