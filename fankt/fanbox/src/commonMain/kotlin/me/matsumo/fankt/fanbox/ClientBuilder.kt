package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.domain.model.db.CSRFToken

internal fun buildHttpClient(
    formatter: Json,
    cookieStorage: PersistentCookieStorage,
    source: FanboxDiagnosticSource,
    csrfToken: CSRFToken? = null,
    logLevel: LogLevel = LogLevel.NONE,
    isEnableContentNegotiation: Boolean = true,
): HttpClient = HttpClient {
    configureFanboxClient(
        formatter = formatter,
        source = source,
        logLevel = logLevel,
        isEnableContentNegotiation = isEnableContentNegotiation,
    )

    install(HttpCookies) {
        storage = cookieStorage
    }

    defaultRequest {
        header("origin", "https://www.fanbox.cc")
        header("referer", "https://www.fanbox.cc/")
        header(
            "user-agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        )
        header("x-csrf-token", csrfToken?.value.orEmpty())
    }
}

internal fun HttpClientConfig<*>.configureFanboxClient(
    formatter: Json,
    source: FanboxDiagnosticSource,
    logLevel: LogLevel = LogLevel.NONE,
    isEnableContentNegotiation: Boolean = true,
) {
    install(Logging) {
        level = when (logLevel) {
            LogLevel.BODY -> LogLevel.INFO
            LogLevel.ALL -> LogLevel.HEADERS
            else -> logLevel
        }
        logger = object : Logger {
            override fun log(message: String) {
                Napier.d(message)
            }
        }
        sanitizeHeader("cookie")
        sanitizeHeader("x-csrf-token")
        sanitizeHeader("authorization")
    }

    if (isEnableContentNegotiation) {
        install(ContentNegotiation) {
            json(formatter)
        }
    }

    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.isSuccess()) return@validateResponse

            val target = FanboxExceptionFactory.target(source, response.request.url)
            if (logLevel != LogLevel.NONE && target.retainResponseFragment) {
                val body = runCatching { response.bodyAsText() }.getOrNull()
                body?.let { Napier.d { "FANBOX response: ${FanboxExceptionFactory.sanitizeFragment(it)}" } }
            }

            throw FanboxExceptionFactory.fromHttpResponse(response, source)
        }

        handleResponseExceptionWithRequest { cause, request ->
            when {
                cause is FanboxException -> throw cause
                cause is CancellationException -> throw cause
                cause is JsonConvertException -> {
                    throw FanboxExceptionFactory.schemaMismatch(
                        response = runCatching { request.call.response }.getOrNull(),
                        request = request,
                        source = source,
                        cause = cause,
                    )
                }
                cause is SerializationException || cause is NoTransformationFoundException -> {
                    val response = runCatching { request.call.response }.getOrNull()
                    if (response != null) {
                        throw FanboxExceptionFactory.schemaMismatch(response, request, source, cause)
                    }
                }
                cause is IOException -> throw FanboxExceptionFactory.network(request, source, cause)
            }
        }
    }
}
