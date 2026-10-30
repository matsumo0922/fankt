package me.matsumo.fankt.fanbox.transport

import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor

internal data class FanboxRawResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val bodyText: String,
)

internal fun interface FanboxRequestExecutor {
    suspend fun execute(descriptor: RequestDescriptor): FanboxRawResponse
}

internal class InvalidRequestDescriptorException(message: String) : IllegalArgumentException(message)
