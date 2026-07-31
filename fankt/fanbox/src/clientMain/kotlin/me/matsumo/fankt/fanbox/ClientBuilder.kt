@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter
import kotlin.time.Clock

internal fun interface FanboxHttpClientFactory {
    fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient
}

internal val DefaultFanboxHttpClientFactory = FanboxHttpClientFactory { block -> HttpClient(block) }

private class FanboxCsrfTokenConfig {
    lateinit var provider: suspend () -> String?
}

internal const val FANBOX_CSRF_HEADER = "x-csrf-token"

private val FanboxCsrfTokenPlugin = createClientPlugin(
    name = "FanboxCsrfToken",
    createConfiguration = ::FanboxCsrfTokenConfig,
) {
    val provider = pluginConfig.provider
    onRequest { request, _ ->
        if (!request.headers.contains(FANBOX_CSRF_HEADER)) {
            request.header(FANBOX_CSRF_HEADER, provider().orEmpty())
        }
    }
}

internal fun buildFanboxExecutorHttpClient(
    cookieStorage: CookiesStorage,
    csrfTokenProvider: suspend () -> String? = { null },
    logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    clientFactory: FanboxHttpClientFactory = DefaultFanboxHttpClientFactory,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        configureFanboxLogging(logLevel)
        configureFanboxAuthenticatedRequest(cookieStorage, csrfTokenProvider, isDownloadClient = false)
        followRedirects = false
    }
    return if (engine == null) {
        clientFactory.create(configure)
    } else {
        HttpClient(engine, configure)
    }
}

internal fun buildFanboxDownloadHttpClient(
    cookieStorage: CookiesStorage,
    csrfTokenProvider: suspend () -> String? = { null },
    logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    clientFactory: FanboxHttpClientFactory = DefaultFanboxHttpClientFactory,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        configureFanboxLogging(logLevel)
        configureFanboxAuthenticatedRequest(cookieStorage, csrfTokenProvider, isDownloadClient = true)
        configureDownloadFailures(logLevel)
    }
    return if (engine == null) {
        clientFactory.create(configure)
    } else {
        HttpClient(engine, configure)
    }
}

private fun HttpClientConfig<*>.configureFanboxAuthenticatedRequest(
    cookieStorage: CookiesStorage,
    csrfTokenProvider: suspend () -> String?,
    isDownloadClient: Boolean,
) {
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
    }

    if (isDownloadClient) {
        install(FanboxDownloadDestinationPlugin) {
            this.csrfTokenProvider = csrfTokenProvider
        }
    } else {
        install(FanboxCsrfTokenPlugin) {
            provider = csrfTokenProvider
        }
    }
}

private fun fanboxDownloadFailure(response: HttpResponse): FanboxException = FanboxFailureInterpreter.httpFailure(
    endpoint = FanboxExceptionFactory.DOWNLOAD_ENDPOINT,
    statusCode = response.status.value,
    rawBody = null,
    retryAfter = response.headers[HttpHeaders.RetryAfter],
    nowEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
)

private fun HttpClientConfig<*>.configureDownloadFailures(logLevel: FanboxLogLevel) {
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.isSuccess()) return@validateResponse

            val failure = fanboxDownloadFailure(response)
            if (logLevel != FanboxLogLevel.NONE) {
                failure.rawBody?.let { body -> Napier.d { "FANBOX response: $body" } }
            }
            throw failure
        }

        handleResponseExceptionWithRequest { cause, _ ->
            when (cause) {
                is FanboxException -> throw cause
                is CancellationException -> throw cause
                is IOException -> throw FanboxExceptionFactory.downloadNetwork(cause)
            }
        }
    }
}

private fun HttpClientConfig<*>.configureFanboxLogging(logLevel: FanboxLogLevel) {
    install(Logging) {
        level = logLevel.toInternalLogLevel()
        logger = object : Logger {
            override fun log(message: String) {
                Napier.d(message)
            }
        }
        sanitizeHeader { header ->
            header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                header.equals(FANBOX_CSRF_HEADER, ignoreCase = true) ||
                header.equals(HttpHeaders.Authorization, ignoreCase = true)
        }
    }
}

internal fun FanboxLogLevel.toInternalLogLevel(): LogLevel = when (this) {
    FanboxLogLevel.NONE -> LogLevel.NONE
    FanboxLogLevel.INFO -> LogLevel.INFO
    FanboxLogLevel.HEADERS -> LogLevel.HEADERS
    FanboxLogLevel.BODY -> LogLevel.INFO
    FanboxLogLevel.ALL -> LogLevel.HEADERS
}
