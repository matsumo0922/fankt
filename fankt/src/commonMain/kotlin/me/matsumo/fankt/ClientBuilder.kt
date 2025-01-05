package me.matsumo.fankt

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.matsumo.fankt.datasource.db.PersistentCookieStorage

internal fun buildHttpClient(
    isEnableContentNegotiation: Boolean,
    cookieStorage: PersistentCookieStorage,
): HttpClient {

    val json = Json {
        isLenient = true
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    val customLogger = object : Logger {
        override fun log(message: String) {
            Napier.d(message)
        }
    }

    val client = HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger = customLogger
        }

        if (isEnableContentNegotiation) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        install(HttpCookies) {
            storage = cookieStorage
        }

        defaultRequest {
            header("origin", "https://www.fanbox.cc")
            header("referer", "https://www.fanbox.cc/")
            header("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        }

        HttpResponseValidator {
            validateResponse {
                val isError = !it.status.isSuccess()
                val isJson = it.contentType()?.match(ContentType.Application.Json) == true
                val response = it.bodyAsText()

                if (isError && isJson) {
                    Napier.d { "JSON: $response" }
                }

                if (it.status == HttpStatusCode.NotFound || it.status.value >= 400) {
                    error("HTTP ${it.status.value}: $response")
                }
            }
        }
    }

    return client
}
