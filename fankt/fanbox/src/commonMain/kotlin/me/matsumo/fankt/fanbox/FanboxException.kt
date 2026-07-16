package me.matsumo.fankt.fanbox

import kotlin.time.Duration

/**
 * A typed failure produced by a FANBOX request.
 *
 * [statusCode] is `null` when no response was received. [rawBody] contains at most a bounded,
 * sanitized diagnostic fragment. A `null` fragment means either that the body was unavailable or
 * that the client intentionally suppressed it. The fragment can still contain FANBOX or user data
 * and must not be treated as credential-free. Only this exception's [message] and [rawBody] are
 * covered by the bounded-diagnostic contract; messages in [cause] are not sanitized.
 *
 * @property statusCode HTTP status code, or `null` when no response was received.
 * @property rawBody bounded response fragment, or `null` when unavailable or suppressed.
 * @property endpoint stable library endpoint label.
 */
public sealed class FanboxException(
    public val statusCode: Int?,
    public val rawBody: String?,
    public val endpoint: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The request requires a valid FANBOX session. */
    public class Unauthorized internal constructor(
        rawBody: String?,
        endpoint: String,
    ) : FanboxException(401, rawBody, endpoint, httpMessage(401, endpoint))

    /** The server refused the request. */
    public open class Forbidden internal constructor(
        rawBody: String?,
        endpoint: String,
    ) : FanboxException(403, rawBody, endpoint, httpMessage(403, endpoint))

    /** The requested FANBOX resource was not found. */
    public class NotFound internal constructor(
        rawBody: String?,
        endpoint: String,
    ) : FanboxException(404, rawBody, endpoint, httpMessage(404, endpoint))

    /** The request was rate limited. */
    public class RateLimited internal constructor(
        rawBody: String?,
        endpoint: String,
        public val retryAfter: Duration?,
    ) : FanboxException(429, rawBody, endpoint, httpMessage(429, endpoint))

    /** FANBOX returned a 5xx response. */
    public class ServerError internal constructor(
        statusCode: Int,
        rawBody: String?,
        endpoint: String,
    ) : FanboxException(statusCode, rawBody, endpoint, httpMessage(statusCode, endpoint))

    /** FANBOX returned an HTTP error that has no more specific public subtype. */
    public class UnexpectedHttpError internal constructor(
        statusCode: Int,
        rawBody: String?,
        endpoint: String,
    ) : FanboxException(statusCode, rawBody, endpoint, httpMessage(statusCode, endpoint))

    /** A response could not be transformed into the library's expected schema. */
    public class SchemaMismatch internal constructor(
        statusCode: Int?,
        rawBody: String?,
        endpoint: String,
        cause: Throwable?,
    ) : FanboxException(
        statusCode = statusCode,
        rawBody = rawBody,
        endpoint = endpoint,
        message = diagnosticMessage("Response schema mismatch", statusCode, endpoint),
        cause = cause,
    )

    /** The request failed before a response was available. */
    public class Network internal constructor(
        endpoint: String,
        cause: Throwable,
    ) : FanboxException(
        statusCode = null,
        rawBody = null,
        endpoint = endpoint,
        message = diagnosticMessage("Network request failed", null, endpoint),
        cause = cause,
    )

    private companion object {
        fun httpMessage(statusCode: Int, endpoint: String): String =
            "FANBOX request failed (HTTP $statusCode, endpoint: $endpoint)"

        fun diagnosticMessage(label: String, statusCode: Int?, endpoint: String): String = buildString {
            append(label)
            append(" (endpoint: ")
            append(endpoint)
            statusCode?.let {
                append(", HTTP ")
                append(it)
            }
            append(')')
        }
    }
}
