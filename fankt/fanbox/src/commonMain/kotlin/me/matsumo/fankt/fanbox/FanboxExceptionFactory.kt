@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.fromHttpToGmtDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal enum class FanboxDiagnosticSource {
    LibraryGenerated,
    Download,
}

internal data class FanboxDiagnosticTarget(
    val endpoint: String,
    val retainResponseFragment: Boolean,
)

internal object FanboxExceptionFactory {
    internal const val MAX_RAW_BODY_LENGTH = 2_048

    private const val CUSTOM_REQUEST = "custom-request"
    private const val DOWNLOAD = "download"
    private const val REDACTED = "[REDACTED]"

    private val apiEndpoints = setOf(
        "bell.list",
        "creator.get",
        "creator.listFollowing",
        "creator.listPixiv",
        "creator.listRecommended",
        "creator.search",
        "follow.create",
        "follow.delete",
        "legacy/support/creator",
        "newsletter.list",
        "payment.listPaid",
        "payment.listUnpaid",
        "plan.listCreator",
        "plan.listSupporting",
        "post.addComment",
        "post.deleteComment",
        "post.getComments",
        "post.info",
        "post.likeComment",
        "post.likePost",
        "post.listCreator",
        "post.listHome",
        "post.listSupporting",
        "post.listTagged",
        "post.paginateCreator",
        "tag.getFeatured",
        "tag.search",
    )

    private const val credentialKey =
        "(?:csrfToken|FANBOXSESSID|x-csrf-token|authorization|cookie)"
    private val jsonCredential = Regex(
        pattern = "(?i)([\\\"']?$credentialKey[\\\"']?\\s*:\\s*[\\\"'])([^\\\"']*)([\\\"'])",
    )
    private val htmlCredential = Regex(
        pattern = "(?i)($credentialKey&amp;quot;\\s*:\\s*&amp;quot;)(.*?)(?=&amp;quot;)",
    )
    private val entityCredential = Regex(
        pattern = "(?i)($credentialKey&quot;\\s*:\\s*&quot;)(.*?)(?=&quot;)",
    )
    private val headerCredential = Regex(
        pattern = "(?i)($credentialKey\\s*[=:]\\s*)([^\\s;,]+)",
    )

    fun target(source: FanboxDiagnosticSource, url: Url): FanboxDiagnosticTarget {
        if (source == FanboxDiagnosticSource.Download) {
            return FanboxDiagnosticTarget(DOWNLOAD, retainResponseFragment = false)
        }

        val path = url.encodedPath.trimStart('/')
        return when {
            url.host == "api.fanbox.cc" && path in apiEndpoints ->
                FanboxDiagnosticTarget(path, retainResponseFragment = true)

            url.host == "www.fanbox.cc" && path.isEmpty() ->
                FanboxDiagnosticTarget("homepage", retainResponseFragment = true)

            else -> FanboxDiagnosticTarget(CUSTOM_REQUEST, retainResponseFragment = false)
        }
    }

    suspend fun fromHttpResponse(
        response: HttpResponse,
        source: FanboxDiagnosticSource,
        nowEpochMilliseconds: Long = Clock.System.now().toEpochMilliseconds(),
    ): FanboxException {
        val target = target(source, response.request.url)
        val rawBody = responseFragment(response, target)
        val statusCode = response.status.value

        return when (statusCode) {
            401 -> FanboxException.Unauthorized(rawBody, target.endpoint)
            403 -> FanboxException.Forbidden(rawBody, target.endpoint)
            404 -> FanboxException.NotFound(rawBody, target.endpoint)
            429 -> FanboxException.RateLimited(
                rawBody = rawBody,
                endpoint = target.endpoint,
                retryAfter = parseRetryAfter(response.headers[HttpHeaders.RetryAfter], nowEpochMilliseconds),
            )
            in 500..599 -> FanboxException.ServerError(statusCode, rawBody, target.endpoint)
            else -> FanboxException.UnexpectedHttpError(statusCode, rawBody, target.endpoint)
        }
    }

    suspend fun schemaMismatch(
        response: HttpResponse?,
        request: HttpRequest,
        source: FanboxDiagnosticSource,
        cause: Throwable,
    ): FanboxException.SchemaMismatch {
        val target = target(source, request.url)
        return FanboxException.SchemaMismatch(
            statusCode = response?.status?.value,
            rawBody = response?.let { responseFragment(it, target) },
            endpoint = target.endpoint,
            cause = cause,
        )
    }

    fun schemaMismatch(
        statusCode: Int?,
        html: String,
        endpoint: String,
        cause: Throwable?,
    ): FanboxException.SchemaMismatch = FanboxException.SchemaMismatch(
        statusCode = statusCode,
        rawBody = sanitizeFragment(html),
        endpoint = endpoint,
        cause = cause,
    )

    fun network(request: HttpRequest, source: FanboxDiagnosticSource, cause: Throwable): FanboxException.Network {
        val target = target(source, request.url)
        return FanboxException.Network(target.endpoint, cause)
    }

    fun sanitizeFragment(body: String): String {
        val redacted = body
            .replace(htmlCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(entityCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(jsonCredential) { match -> match.groupValues[1] + REDACTED + match.groupValues[3] }
            .replace(headerCredential) { match -> match.groupValues[1] + REDACTED }
        val normalized = buildString(redacted.length) {
            redacted.forEach { char ->
                append(if (char.code < 32 || char.code == 127) ' ' else char)
            }
        }

        if (normalized.length <= MAX_RAW_BODY_LENGTH) return normalized
        return normalized.take(MAX_RAW_BODY_LENGTH - 1) + '…'
    }

    fun parseRetryAfter(value: String?, nowEpochMilliseconds: Long): Duration? {
        if (value == null) return null

        value.trim().toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return seconds.seconds.takeIf { it.isFinite() }
        }

        val targetEpochMilliseconds = runCatching { value.fromHttpToGmtDate().timestamp }.getOrNull()
            ?: return null
        val deltaMilliseconds = targetEpochMilliseconds - nowEpochMilliseconds
        if ((targetEpochMilliseconds > nowEpochMilliseconds && deltaMilliseconds < 0) ||
            (targetEpochMilliseconds < nowEpochMilliseconds && deltaMilliseconds > 0)
        ) {
            return null
        }
        if (targetEpochMilliseconds <= nowEpochMilliseconds) return Duration.ZERO
        return deltaMilliseconds.milliseconds.takeIf { it.isFinite() }
    }

    private suspend fun responseFragment(
        response: HttpResponse,
        target: FanboxDiagnosticTarget,
    ): String? {
        if (!target.retainResponseFragment) return null
        return runCatching { response.bodyAsText() }.getOrNull()?.let(::sanitizeFragment)
    }
}
