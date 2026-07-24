@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.transport.ktor

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxLogLevel
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter
import me.matsumo.fankt.fanbox.transport.FanboxCredentialBehavior
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import me.matsumo.fankt.fanbox.transport.InvalidRequestDescriptorException
import me.matsumo.fankt.fanbox.transport.TrustedFanboxEndpoint
import kotlin.time.Clock

internal class KtorFanboxRequestExecutor(
    internal val client: HttpClient,
    private val logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    private val additionalHeaders: suspend (RequestDescriptor) -> Headers = { Headers.Empty },
    private val nowEpochMilliseconds: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) : FanboxRequestExecutor {

    init {
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
    }

    override suspend fun execute(descriptor: RequestDescriptor): FanboxRawResponse {
        val validated = FanboxDescriptorValidator.validate(descriptor)
        when (validated.policy.credentialBehavior) {
            FanboxCredentialBehavior.SHARED_FANBOX_AUTHENTICATION -> Unit
        }
        return try {
            executeValidated(
                descriptor = descriptor,
                policy = validated.policy,
                initialUrl = validated.url,
                initialMethod = validated.method,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: FanboxException) {
            throw failure
        } catch (failure: InvalidRequestDescriptorException) {
            throw failure
        } catch (failure: IOException) {
            throw FanboxFailureInterpreter.network(descriptor.endpointId.value, failure)
        }
    }

    private suspend fun executeValidated(
        descriptor: RequestDescriptor,
        policy: TrustedFanboxEndpoint,
        initialUrl: Url,
        initialMethod: FanboxHttpMethod,
    ): FanboxRawResponse {
        var url = initialUrl
        var method = initialMethod
        var redirectCount = 0

        while (true) {
            val response = client.request(url) {
                this.method = method.toKtorMethod()
                headers.appendAll(additionalHeaders(descriptor))
                if (method == FanboxHttpMethod.POST) {
                    descriptor.jsonBody?.let { body ->
                        setBody(TextContent(body, ContentType.Application.Json))
                    }
                }
            }
            val body = response.bodyAsText()

            if (response.status.isHandledRedirect()) {
                if (redirectCount >= maxRedirects) {
                    throw InvalidRequestDescriptorException("FANBOX redirect limit exceeded")
                }
                val redirectedMethod = response.status.redirectedMethod(method)
                url = FanboxDescriptorValidator.validateRedirect(
                    currentUrl = url,
                    location = response.headers[HttpHeaders.Location],
                    redirectedMethod = redirectedMethod,
                    policy = policy,
                )
                method = redirectedMethod
                redirectCount += 1
                continue
            }

            if (!response.status.isSuccess()) {
                val failure = FanboxFailureInterpreter.httpFailure(
                    endpoint = descriptor.endpointId.value,
                    statusCode = response.status.value,
                    rawBody = body,
                    retryAfter = response.headers[HttpHeaders.RetryAfter],
                    nowEpochMilliseconds = nowEpochMilliseconds(),
                )
                if (logLevel != FanboxLogLevel.NONE) {
                    failure.rawBody?.let { fragment -> Napier.d { "FANBOX response: $fragment" } }
                }
                throw failure
            }

            return FanboxRawResponse(
                statusCode = response.status.value,
                headers = response.headers.entries().associate { (name, values) -> name to values.toList() },
                bodyText = body,
            )
        }
    }

    private fun FanboxHttpMethod.toKtorMethod(): HttpMethod = when (this) {
        FanboxHttpMethod.GET -> HttpMethod.Get
        FanboxHttpMethod.POST -> HttpMethod.Post
    }

    private fun HttpStatusCode.isHandledRedirect(): Boolean = value in HANDLED_REDIRECT_STATUS_CODES

    private fun HttpStatusCode.redirectedMethod(currentMethod: FanboxHttpMethod): FanboxHttpMethod = when (value) {
        HttpStatusCode.MovedPermanently.value,
        HttpStatusCode.Found.value,
        -> if (currentMethod == FanboxHttpMethod.POST) FanboxHttpMethod.GET else currentMethod

        HttpStatusCode.TemporaryRedirect.value,
        HttpStatusCode.PermanentRedirect.value,
        -> currentMethod

        else -> error("Unsupported redirect status: $value")
    }

    private companion object {
        const val DEFAULT_MAX_REDIRECTS = 5

        val HANDLED_REDIRECT_STATUS_CODES = setOf(
            HttpStatusCode.MovedPermanently.value,
            HttpStatusCode.Found.value,
            HttpStatusCode.TemporaryRedirect.value,
            HttpStatusCode.PermanentRedirect.value,
        )
    }
}
