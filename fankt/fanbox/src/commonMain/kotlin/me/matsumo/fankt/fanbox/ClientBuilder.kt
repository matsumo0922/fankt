package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.domain.model.db.CSRFToken

private val FanboxResponseAttributeKey = AttributeKey<HttpResponse>("FanboxResponse")

internal fun interface FanboxHttpClientFactory {
    fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient
}

internal val DefaultFanboxHttpClientFactory = FanboxHttpClientFactory { block -> HttpClient(block) }

private class FanboxCsrfTokenConfig {
    lateinit var provider: suspend () -> CSRFToken?
}

private val FanboxCsrfTokenPlugin = createClientPlugin(
    name = "FanboxCsrfToken",
    createConfiguration = ::FanboxCsrfTokenConfig,
) {
    val provider = pluginConfig.provider
    onRequest { request, _ ->
        if (!request.headers.contains("x-csrf-token")) {
            request.header("x-csrf-token", provider()?.value.orEmpty())
        }
    }
}

private class FanboxSchemaMismatchConfig {
    lateinit var source: FanboxDiagnosticSource
}

private val FanboxSchemaMismatchPlugin = createClientPlugin(
    name = "FanboxSchemaMismatch",
    createConfiguration = ::FanboxSchemaMismatchConfig,
) {
    val source = pluginConfig.source
    transformResponseBody { response, content, requestedType ->
        if (requestedType.type == String::class) {
            return@transformResponseBody (response.charset() ?: Charsets.UTF_8)
                .newDecoder()
                .decode(content.readRemaining())
        }

        val cause = NoTransformationFoundException(response, content::class, requestedType.type)
        throw FanboxExceptionFactory.schemaMismatch(response, response.request, source, cause)
    }
}

internal fun buildHttpClient(
    formatter: Json,
    cookieStorage: CookiesStorage,
    source: FanboxDiagnosticSource,
    csrfTokenProvider: suspend () -> CSRFToken? = { null },
    logLevel: LogLevel = LogLevel.NONE,
    isEnableContentNegotiation: Boolean = true,
    clientFactory: FanboxHttpClientFactory = DefaultFanboxHttpClientFactory,
    engine: HttpClientEngine? = null,
): HttpClient {
    return if (engine == null) {
        clientFactory.create {
            configureFanboxHttpClient(
                formatter,
                cookieStorage,
                source,
                csrfTokenProvider,
                logLevel,
                isEnableContentNegotiation,
            )
        }
    } else {
        HttpClient(engine) {
            configureFanboxHttpClient(
                formatter,
                cookieStorage,
                source,
                csrfTokenProvider,
                logLevel,
                isEnableContentNegotiation,
            )
        }
    }
}

private fun HttpClientConfig<*>.configureFanboxHttpClient(
    formatter: Json,
    cookieStorage: CookiesStorage,
    source: FanboxDiagnosticSource,
    csrfTokenProvider: suspend () -> CSRFToken?,
    logLevel: LogLevel,
    isEnableContentNegotiation: Boolean,
) {
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
    }

    install(FanboxCsrfTokenPlugin) {
        provider = csrfTokenProvider
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
        sanitizeHeader { header ->
            header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                header.equals("x-csrf-token", ignoreCase = true) ||
                header.equals(HttpHeaders.Authorization, ignoreCase = true)
        }
    }

    if (isEnableContentNegotiation) {
        install(ContentNegotiation) {
            json(formatter)
        }
        install(FanboxSchemaMismatchPlugin) {
            this.source = source
        }
    }

    HttpResponseValidator {
        validateResponse { response ->
            response.request.attributes.put(FanboxResponseAttributeKey, response)
            if (response.status.isSuccess()) return@validateResponse

            val target = FanboxExceptionFactory.target(source, response.request.url)
            val failure = FanboxExceptionFactory.fromHttpResponse(response, source)
            if (logLevel != LogLevel.NONE && target.retainResponseFragment) {
                failure.rawBody?.let { body -> Napier.d { "FANBOX response: $body" } }
            }

            throw failure
        }

        handleResponseExceptionWithRequest { cause, request ->
            when {
                cause is FanboxException -> throw cause
                cause is CancellationException -> throw cause
                cause is JsonConvertException -> {
                    throw FanboxExceptionFactory.schemaMismatch(
                        response = request.responseOrNull(),
                        request = request,
                        source = source,
                        cause = cause,
                    )
                }
                cause is SerializationException || cause is NoTransformationFoundException -> {
                    val response = request.responseOrNull()
                    if (response != null) {
                        throw FanboxExceptionFactory.schemaMismatch(response, request, source, cause)
                    }
                }
                cause is IOException -> throw FanboxExceptionFactory.network(request, source, cause)
            }
        }
    }
}

private fun io.ktor.client.request.HttpRequest.responseOrNull(): HttpResponse? =
    attributes.getOrNull(FanboxResponseAttributeKey)
        ?: runCatching { call.response }.getOrNull()
