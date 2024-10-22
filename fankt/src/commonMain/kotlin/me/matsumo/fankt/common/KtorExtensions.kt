package me.matsumo.fankt.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun Map<String, Any>.toJsonObject(): JsonObject {
    return buildJsonObject {
        for ((key, value) in this@toJsonObject) {
            put(key, value.toString())
        }
    }
}

internal fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}
