package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.parseUrl

internal class FanboxDownloadDestinationConfig {
    lateinit var csrfTokenProvider: suspend () -> String?
}

internal val FanboxDownloadDestinationPlugin = createClientPlugin(
    name = "FanboxDownloadDestination",
    createConfiguration = ::FanboxDownloadDestinationConfig,
) {
    val csrfTokenProvider = pluginConfig.csrfTokenProvider

    on(SendingRequest) { request, _ ->
        val url = request.url.build()
        validateFanboxDownloadUrl(url)

        if (url.isFanboxHost()) {
            if (!request.headers.contains(FANBOX_CSRF_HEADER)) {
                request.header(FANBOX_CSRF_HEADER, csrfTokenProvider().orEmpty())
            }
        } else {
            request.headers.remove(FANBOX_CSRF_HEADER)
        }
    }
}

internal fun parseFanboxDownloadUrl(value: String): Url {
    val url = parseUrl(value)
        ?: throw IllegalArgumentException("Download URL must be an absolute HTTPS URL")
    validateFanboxDownloadUrl(url)
    return url
}

internal fun validateFanboxDownloadUrl(url: Url) {
    require(url.protocol == URLProtocol.HTTPS) { "Download URL must use HTTPS" }
    require(url.user == null && url.password == null) { "Download URL must not contain userinfo" }
    require(url.isAllowedFanboxDownloadHost()) { "Download URL host is not allowed" }
}

private fun Url.isAllowedFanboxDownloadHost(): Boolean {
    val normalizedHost = host.lowercase()
    return normalizedHost.isFanboxHost() || normalizedHost in EXTERNAL_MEDIA_HOSTS
}

private fun Url.isFanboxHost(): Boolean = host.lowercase().isFanboxHost()

private fun String.isFanboxHost(): Boolean = this == FANBOX_HOST || endsWith(".$FANBOX_HOST")

private const val FANBOX_HOST = "fanbox.cc"
private val EXTERNAL_MEDIA_HOSTS = setOf("pixiv.pximg.net", "fanbox.pixiv.net")
