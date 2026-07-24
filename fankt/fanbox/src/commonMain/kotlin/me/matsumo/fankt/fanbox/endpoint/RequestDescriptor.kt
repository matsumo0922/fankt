package me.matsumo.fankt.fanbox.endpoint

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
internal value class FanboxEndpointId(internal val value: String)

@Serializable
internal enum class FanboxHttpMethod {
    GET,
    POST,
}

@Serializable
internal data class FanboxQueryParameter(
    internal val name: String,
    internal val value: String,
)

@Serializable
internal data class RequestDescriptor(
    internal val endpointId: FanboxEndpointId,
    internal val path: String,
    internal val method: FanboxHttpMethod,
    internal val query: List<FanboxQueryParameter> = emptyList(),
    internal val jsonBody: String? = null,
)
