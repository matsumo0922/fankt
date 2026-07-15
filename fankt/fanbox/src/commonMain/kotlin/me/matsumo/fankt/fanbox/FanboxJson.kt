package me.matsumo.fankt.fanbox

import kotlinx.serialization.json.Json

internal fun createFanboxJson(): Json = Json {
    isLenient = true
    prettyPrint = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
