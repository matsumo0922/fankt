package me.matsumo.fankt.fanbox.transport.ktor

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.transport.InvalidRequestDescriptorException
import me.matsumo.fankt.fanbox.transport.TrustedFanboxEndpoint
import me.matsumo.fankt.fanbox.transport.TrustedFanboxEndpointPolicy

internal data class ValidatedFanboxRequest(
    val policy: TrustedFanboxEndpoint,
    val url: Url,
    val method: FanboxHttpMethod,
)

internal object FanboxDescriptorValidator {
    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun validate(descriptor: RequestDescriptor): ValidatedFanboxRequest {
        val policy = TrustedFanboxEndpointPolicy.resolve(descriptor.endpointId)
            ?: invalid("Unknown FANBOX endpoint ID")
        if (descriptor.method != policy.method) {
            invalid("FANBOX endpoint method does not match trusted policy")
        }

        validateRelativePath(descriptor)
        descriptor.query.forEach { parameter ->
            if (parameter.name.isEmpty() || parameter.name.hasControlCharacter()) {
                invalid("FANBOX query parameter name is invalid")
            }
        }

        val url = try {
            URLBuilder(policy.origin).apply {
                encodedPath = if (descriptor.path.isEmpty()) "/" else "/${descriptor.path}"
                descriptor.query.forEach { parameter ->
                    parameters.append(parameter.name, parameter.value)
                }
            }.build()
        } catch (_: IllegalArgumentException) {
            invalid("FANBOX request URL is malformed")
        }
        validateFinalUrl(url, policy)

        return ValidatedFanboxRequest(policy, url, descriptor.method)
    }

    fun validateRedirect(
        currentUrl: Url,
        location: String?,
        redirectedMethod: FanboxHttpMethod,
        policy: TrustedFanboxEndpoint,
    ): Url {
        if (location.isNullOrEmpty() || location != location.trim() || location.hasControlCharacter()) {
            invalid("FANBOX redirect location is invalid")
        }
        if (redirectedMethod != policy.method) {
            invalid("FANBOX redirect method does not match trusted policy")
        }

        val redirectedUrl = try {
            URLBuilder(currentUrl).takeFrom(location).build()
        } catch (_: IllegalArgumentException) {
            invalid("FANBOX redirect location is malformed")
        }
        validateFinalUrl(redirectedUrl, policy)
        return redirectedUrl
    }

    private fun validateRelativePath(descriptor: RequestDescriptor) {
        val path = descriptor.path
        if (path.isEmpty()) {
            if (descriptor.endpointId != FanboxEndpointIds.homepage || descriptor.method != FanboxHttpMethod.GET) {
                invalid("Empty FANBOX path is allowed only for homepage GET")
            }
            return
        }
        if (descriptor.endpointId == FanboxEndpointIds.homepage) {
            invalid("FANBOX homepage path must be empty")
        }
        if (
            path.startsWith('/') ||
            path.startsWith("//") ||
            SCHEME_PREFIX.containsMatchIn(path) ||
            path.contains('@') ||
            path.contains('?') ||
            path.contains('#') ||
            path.contains('\\') ||
            path.contains('%') ||
            path.hasControlCharacter() ||
            path.any(Char::isWhitespace)
        ) {
            invalid("FANBOX path must be a plain relative path")
        }
        val segments = path.split('/')
        if (segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
            invalid("FANBOX path contains an invalid traversal segment")
        }
    }

    private fun validateFinalUrl(url: Url, policy: TrustedFanboxEndpoint) {
        val trustedOrigin = try {
            Url(policy.origin)
        } catch (_: IllegalArgumentException) {
            invalid("Trusted FANBOX origin is malformed")
        }
        if (
            url.protocol != URLProtocol.HTTPS ||
            url.protocol != trustedOrigin.protocol ||
            url.host != trustedOrigin.host ||
            url.port != trustedOrigin.port ||
            !url.user.isNullOrEmpty() ||
            !url.password.isNullOrEmpty() ||
            url.fragment.isNotEmpty()
        ) {
            invalid("FANBOX request escaped its trusted origin")
        }

        val decodedSegments = try {
            url.encodedPath.split('/').map(String::decodeURLPart)
        } catch (_: IllegalArgumentException) {
            invalid("FANBOX request path is malformed")
        }
        if (decodedSegments.any { segment -> segment == "." || segment == ".." || segment.contains('\\') }) {
            invalid("FANBOX request path contains a traversal segment")
        }
    }

    private fun String.hasControlCharacter(): Boolean = any { character -> character.code < 0x20 || character.code == 0x7f }

    private fun invalid(message: String): Nothing = throw InvalidRequestDescriptorException(message)
}
