@file:Suppress("unused")
@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient

public typealias AndroidKtorClientAlias = HttpClient

// kotlinx-datetime is absent by design, so this proxy proves the generic forbidden-marker path.
public typealias AndroidGenericMarkerProxyAlias = kotlin.time.Instant
