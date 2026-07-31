package me.matsumo.fankt.fanbox.response

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal fun interface FanboxDiagnosticSink {
    fun report(message: String)

    companion object {
        val none = FanboxDiagnosticSink {}
    }
}

internal object FanboxDiagnostics {
    const val MAX_FRAGMENT_LENGTH = 2_048

    private const val MAX_JSON_FRAGMENT_DEPTH = 32
    private const val REDACTED = "[REDACTED]"
    private const val REDACTED_JSON_STRING = "\"[REDACTED]\""
    private const val CREDENTIAL_KEY =
        "(?:csrfToken|FANBOXSESSID|x-csrf-token|authorization|cookie)"

    private val jsonCredential = Regex(
        pattern = "([\"']?$CREDENTIAL_KEY[\"']?\\s*:\\s*[\"'])([^\"']*)([\"'])",
        option = RegexOption.IGNORE_CASE,
    )
    private val htmlCredential = Regex(
        pattern = "($CREDENTIAL_KEY&amp;quot;\\s*:\\s*&amp;quot;)(.*?)(?=&amp;quot;)",
        option = RegexOption.IGNORE_CASE,
    )
    private val entityCredential = Regex(
        pattern = "($CREDENTIAL_KEY&quot;\\s*:\\s*&quot;)(.*?)(?=&quot;)",
        option = RegexOption.IGNORE_CASE,
    )
    private val headerCredential = Regex(
        pattern = "($CREDENTIAL_KEY\\s*[=:]\\s*)([^\\s;,]+)",
        option = RegexOption.IGNORE_CASE,
    )
    private val credentialKeys = setOf(
        "csrftoken",
        "fanboxsessid",
        "x-csrf-token",
        "authorization",
        "cookie",
    )

    fun sanitizeFragment(body: String): String {
        val redacted = body
            .replace(htmlCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(entityCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(jsonCredential) { match -> match.groupValues[1] + REDACTED + match.groupValues[3] }
            .replace(headerCredential) { match -> match.groupValues[1] + REDACTED }
        val normalized = buildString(redacted.length) {
            redacted.forEach { char ->
                append(if (char.code < 32 || char.code == 127) ' ' else char)
            }
        }

        if (normalized.length <= MAX_FRAGMENT_LENGTH) return normalized
        return normalized.take(MAX_FRAGMENT_LENGTH - 1) + '…'
    }

    fun listItemMismatchMessage(
        endpoint: String,
        indexPath: List<Int>,
        item: JsonElement,
        includeRawFragment: Boolean,
    ): String = buildString {
        append("FANBOX list item schema mismatch (endpoint: ")
        append(endpoint)
        append(", indexPath: ")
        append(indexPath.joinToString(prefix = "[", postfix = "]"))
        if (includeRawFragment) {
            append(", raw: ")
            append(sanitizeFragment(boundedJsonFragment(item)))
        }
        append(')')
    }

    private fun boundedJsonFragment(element: JsonElement): String {
        val writer = BoundedJsonWriter(MAX_FRAGMENT_LENGTH)
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
                    '' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
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
}
