package me.matsumo.fankt.fanbox

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics

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
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
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
        diagnosticSink.report(
            FanboxDiagnostics.listItemMismatchMessage(
                endpoint = endpoint,
                indexPath = indexPath,
                item = item,
                includeRawFragment = includeRawFragment,
            ),
        )
        return FanboxListItemSchemaMismatch(endpoint, indexPath)
    }
}
