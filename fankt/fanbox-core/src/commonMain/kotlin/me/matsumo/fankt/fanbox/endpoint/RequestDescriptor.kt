package me.matsumo.fankt.fanbox.endpoint

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.InternalFanboxApi
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@InternalFanboxApi
public value class FanboxEndpointId(public val value: String)

@Serializable
@InternalFanboxApi
public enum class FanboxHttpMethod {
    GET,
    POST,
}

@Serializable
@InternalFanboxApi
public data class FanboxQueryParameter(public val name: String, public val value: String)

@Serializable
@InternalFanboxApi
public data class RequestDescriptor(
    public val endpointId: FanboxEndpointId,
    public val path: String,
    public val method: FanboxHttpMethod,
    public val query: List<FanboxQueryParameter> = emptyList(),
    public val jsonBody: String? = null,
)
