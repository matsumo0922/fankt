@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter
import kotlin.time.Clock
import kotlin.time.Duration

internal enum class FanboxDiagnosticSource {
    LibraryGenerated,
    Download,
}

internal typealias FanboxDiagnosticTarget = me.matsumo.fankt.fanbox.response.FanboxDiagnosticTarget

internal object FanboxExceptionFactory {
    internal const val MAX_RAW_BODY_LENGTH = FanboxDiagnostics.MAX_FRAGMENT_LENGTH

    fun target(source: FanboxDiagnosticSource, url: Url): FanboxDiagnosticTarget =
        FanboxFailureInterpreter.target(source == FanboxDiagnosticSource.Download, url.toString())

    suspend fun fromHttpResponse(
        response: HttpResponse,
        source: FanboxDiagnosticSource,
        nowEpochMilliseconds: Long = Clock.System.now().toEpochMilliseconds(),
    ): FanboxException {
        val target = target(source, response.request.url)
        return FanboxFailureInterpreter.httpFailure(
            endpoint = target.endpoint,
            statusCode = response.status.value,
            rawBody = responseFragment(response, target),
            retryAfter = response.headers[HttpHeaders.RetryAfter],
            nowEpochMilliseconds = nowEpochMilliseconds,
        )
    }

    suspend fun schemaMismatch(
        response: HttpResponse?,
        request: HttpRequest,
        source: FanboxDiagnosticSource,
        cause: Throwable,
    ): FanboxException.SchemaMismatch {
        val target = target(source, request.url)
        return FanboxFailureInterpreter.schemaMismatch(
            statusCode = response?.status?.value,
            body = response?.let { responseFragment(it, target) },
            endpoint = target.endpoint,
            cause = cause,
        )
    }

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

    fun network(request: HttpRequest, source: FanboxDiagnosticSource, cause: Throwable): FanboxException.Network {
        val target = target(source, request.url)
        return FanboxFailureInterpreter.network(target.endpoint, cause)
    }

    fun sanitizeFragment(body: String): String = FanboxDiagnostics.sanitizeFragment(body)

    fun parseRetryAfter(value: String?, nowEpochMilliseconds: Long): Duration? =
        FanboxFailureInterpreter.parseRetryAfter(value, nowEpochMilliseconds)

    private suspend fun responseFragment(
        response: HttpResponse,
        target: FanboxDiagnosticTarget,
    ): String? {
        if (!target.retainResponseFragment) return null
        return runCatching { response.bodyAsText() }.getOrNull()
    }
}
