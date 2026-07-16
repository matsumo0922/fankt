package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Napier
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Identifies one item skipped from a FANBOX list response.
 *
 * [indexPath] contains zero-based indexes from the outermost list to the failed item. For example,
 * a failed second reply under the first comment uses `[0, 1]`. Callback overloads report the event
 * on the caller's coroutine context before returning their partial result.
 */
public data class FanboxListItemSchemaMismatch(
    public val endpoint: String,
    public val indexPath: List<Int>,
)

internal data class FanboxTolerantResult<T>(
    val value: T,
    val mismatches: List<FanboxListItemSchemaMismatch>,
)

internal class FanboxListItemDecoder(
    private val formatter: Json = createFanboxJson(),
    private val includeRawFragment: Boolean = false,
) {
    fun <T, R> decodeAndMap(
        endpoint: String,
        items: List<JsonElement>,
        deserializer: DeserializationStrategy<T>,
        indexPrefix: List<Int> = emptyList(),
        transform: (T, List<Int>) -> FanboxTolerantResult<R>,
    ): FanboxTolerantResult<List<R>> {
        val values = ArrayList<R>(items.size)
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()

        items.forEachIndexed { index, item ->
            val indexPath = indexPrefix + index
            try {
                val transformed = transform(formatter.decodeFromJsonElement(deserializer, item), indexPath)
                values += transformed.value
                mismatches += transformed.mismatches
            } catch (_: SerializationException) {
                mismatches += reportMismatch(endpoint, indexPath, item)
            } catch (_: IllegalArgumentException) {
                mismatches += reportMismatch(endpoint, indexPath, item)
            }
        }

        return FanboxTolerantResult(values, mismatches)
    }

    private fun reportMismatch(
        endpoint: String,
        indexPath: List<Int>,
        item: JsonElement,
    ): FanboxListItemSchemaMismatch {
        val mismatch = FanboxListItemSchemaMismatch(endpoint, indexPath)
        Napier.w {
            buildString {
                append("FANBOX list item schema mismatch (endpoint: ")
                append(endpoint)
                append(", indexPath: ")
                append(indexPath.joinToString(prefix = "[", postfix = "]"))
                if (includeRawFragment) {
                    append(", raw: ")
                    append(FanboxExceptionFactory.sanitizeFragment(boundedJsonFragment(item)))
                }
                append(')')
            }
        }
        return mismatch
    }
}

private const val MAX_JSON_FRAGMENT_LENGTH = FanboxExceptionFactory.MAX_RAW_BODY_LENGTH
private const val MAX_JSON_FRAGMENT_DEPTH = 32
private const val REDACTED_JSON_STRING = "\"[REDACTED]\""

private val credentialKeys = setOf(
    "csrftoken",
    "fanboxsessid",
    "x-csrf-token",
    "authorization",
    "cookie",
)

private fun boundedJsonFragment(element: JsonElement): String {
    val writer = BoundedJsonWriter(MAX_JSON_FRAGMENT_LENGTH)
    writer.appendElement(element, depth = 0)
    return writer.build()
}

private class BoundedJsonWriter(
    private val limit: Int,
) {
    private val buffer = StringBuilder(limit)
    private var truncated = false

    fun appendElement(element: JsonElement, depth: Int) {
        if (truncated) return
        if (depth >= MAX_JSON_FRAGMENT_DEPTH) {
            appendJsonString("…")
            return
        }

        when (element) {
            is JsonObject -> appendObject(element, depth)
            is JsonArray -> appendArray(element, depth)
            is JsonPrimitive -> appendPrimitive(element)
        }
    }

    private fun appendObject(value: JsonObject, depth: Int) {
        append('{')
        value.entries.forEachIndexed { index, (key, element) ->
            if (truncated) return@forEachIndexed
            if (index > 0) append(',')
            appendJsonString(key)
            append(':')
            if (key.lowercase() in credentialKeys) {
                append(REDACTED_JSON_STRING)
            } else {
                appendElement(element, depth + 1)
            }
        }
        append('}')
    }

    private fun appendArray(value: JsonArray, depth: Int) {
        append('[')
        value.forEachIndexed { index, element ->
            if (truncated) return@forEachIndexed
            if (index > 0) append(',')
            appendElement(element, depth + 1)
        }
        append(']')
    }

    private fun appendPrimitive(value: JsonPrimitive) {
        when {
            value === JsonNull -> append("null")
            value.isString -> appendJsonString(value.content)
            value.booleanOrNull != null -> append(value.content)
            else -> append(value.content)
        }
    }

    private fun appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            if (truncated) return@forEach
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun append(value: Char) {
        if (buffer.length < limit) buffer.append(value) else truncated = true
    }

    private fun append(value: String) {
        value.forEach(::append)
    }

    fun build(): String {
        if (!truncated) return buffer.toString()
        if (buffer.isEmpty()) return ""
        buffer[buffer.lastIndex] = '…'
        return buffer.toString()
    }
}
