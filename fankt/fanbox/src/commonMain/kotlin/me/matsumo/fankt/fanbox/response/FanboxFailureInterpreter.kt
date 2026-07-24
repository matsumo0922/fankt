@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.response

import me.matsumo.fankt.fanbox.FanboxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal data class FanboxDiagnosticTarget(
    val endpoint: String,
    val retainResponseFragment: Boolean,
)

internal object FanboxFailureInterpreter {
    private const val CUSTOM_REQUEST = "custom-request"
    private const val DOWNLOAD = "download"

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
    private val HTTP_DATE = Regex(
        "^[A-Za-z]{3}, (\\d{2}) ([A-Za-z]{3}) (\\d{4}) (\\d{2}):(\\d{2}):(\\d{2}) GMT$",
    )
    private val MONTHS = mapOf(
        "Jan" to 1,
        "Feb" to 2,
        "Mar" to 3,
        "Apr" to 4,
        "May" to 5,
        "Jun" to 6,
        "Jul" to 7,
        "Aug" to 8,
        "Sep" to 9,
        "Oct" to 10,
        "Nov" to 11,
        "Dec" to 12,
    )

    fun target(isDownload: Boolean, url: String): FanboxDiagnosticTarget {
        if (isDownload) return FanboxDiagnosticTarget(DOWNLOAD, retainResponseFragment = false)

        val parts = runCatching { FanboxUrlParts.parse(url) }.getOrNull()
            ?: return FanboxDiagnosticTarget(CUSTOM_REQUEST, retainResponseFragment = false)
        val path = parts.encodedPath.trimStart('/')
        return when {
            parts.host.equals("api.fanbox.cc", ignoreCase = true) && path in apiEndpoints ->
                FanboxDiagnosticTarget(path, retainResponseFragment = true)

            parts.host.equals("www.fanbox.cc", ignoreCase = true) && path.isEmpty() ->
                FanboxDiagnosticTarget("homepage", retainResponseFragment = true)

            else -> FanboxDiagnosticTarget(CUSTOM_REQUEST, retainResponseFragment = false)
        }
    }

    fun httpFailure(
        endpoint: String,
        statusCode: Int,
        rawBody: String?,
        retryAfter: String?,
        nowEpochMilliseconds: Long,
    ): FanboxException {
        val fragment = rawBody?.let(FanboxDiagnostics::sanitizeFragment)
        return when (statusCode) {
            401 -> FanboxException.Unauthorized(fragment, endpoint)
            403 -> FanboxException.Forbidden(fragment, endpoint)
            404 -> FanboxException.NotFound(fragment, endpoint)
            429 -> FanboxException.RateLimited(
                rawBody = fragment,
                endpoint = endpoint,
                retryAfter = parseRetryAfter(retryAfter, nowEpochMilliseconds),
            )
            in 500..599 -> FanboxException.ServerError(statusCode, fragment, endpoint)
            else -> FanboxException.UnexpectedHttpError(statusCode, fragment, endpoint)
        }
    }

    fun schemaMismatch(
        statusCode: Int?,
        body: String?,
        endpoint: String,
        cause: Throwable?,
    ): FanboxException.SchemaMismatch = FanboxException.SchemaMismatch(
        statusCode = statusCode,
        rawBody = body?.let(FanboxDiagnostics::sanitizeFragment),
        endpoint = endpoint,
        cause = cause,
    )

    fun network(endpoint: String, cause: Throwable): FanboxException.Network =
        FanboxException.Network(endpoint, cause)

    fun parseRetryAfter(value: String?, nowEpochMilliseconds: Long): Duration? {
        if (value == null) return null

        value.trim().toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return seconds.seconds.takeIf { it.isFinite() }
        }

        val targetEpochMilliseconds = parseHttpDate(value) ?: return null
        val deltaMilliseconds = targetEpochMilliseconds - nowEpochMilliseconds
        if ((targetEpochMilliseconds > nowEpochMilliseconds && deltaMilliseconds < 0) ||
            (targetEpochMilliseconds < nowEpochMilliseconds && deltaMilliseconds > 0)
        ) {
            return null
        }
        if (targetEpochMilliseconds <= nowEpochMilliseconds) return Duration.ZERO
        return deltaMilliseconds.milliseconds.takeIf { it.isFinite() }
    }

    private fun parseHttpDate(value: String): Long? {
        val match = HTTP_DATE.matchEntire(value.trim()) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = MONTHS[match.groupValues[2]] ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null
        val second = match.groupValues[6].toIntOrNull() ?: return null
        val iso = buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
            append('T')
            append(hour.toString().padStart(2, '0'))
            append(':')
            append(minute.toString().padStart(2, '0'))
            append(':')
            append(second.toString().padStart(2, '0'))
            append('Z')
        }
        return runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
    }
}
