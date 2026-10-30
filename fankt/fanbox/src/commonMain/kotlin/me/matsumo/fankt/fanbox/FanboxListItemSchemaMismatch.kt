package me.matsumo.fankt.fanbox

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink

/**
 * Identifies one item skipped from a FANBOX list response.
 *
 * [indexPath] contains zero-based indexes from the outermost list to the failed item. For example,
 * a failed second reply under the first comment uses `[0, 1]`. Callback overloads report the event
 * on the caller's coroutine context before returning their partial result.
 */
@Serializable
public data class FanboxListItemSchemaMismatch(public val endpoint: String, public val indexPath: List<Int>)

/** 一覧の解析結果と、読み飛ばした要素。guest から host へ bridge を越えて渡る。 */
@Serializable
internal data class FanboxTolerantResult<T>(val value: T, val mismatches: List<FanboxListItemSchemaMismatch>)

/** 読み飛ばした要素を、要素の中身を含めずに報告する。 */
internal fun FanboxDiagnosticSink.report(mismatch: FanboxListItemSchemaMismatch) = report(
    "FANBOX list item schema mismatch (endpoint: ${mismatch.endpoint}, indexPath: ${mismatch.indexPath})",
)

/** 読み飛ばした要素を [sink] へ報告し、この結果を返す。 */
internal fun <T> FanboxTolerantResult<T>.reportedTo(sink: FanboxDiagnosticSink): FanboxTolerantResult<T> = also {
    mismatches.forEach(sink::report)
}

internal class FanboxListItemDecoder(private val formatter: Json = createFanboxJson()) {
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
                mismatches += FanboxListItemSchemaMismatch(endpoint, indexPath)
            } catch (_: IllegalArgumentException) {
                mismatches += FanboxListItemSchemaMismatch(endpoint, indexPath)
            }
        }

        return FanboxTolerantResult(values, mismatches)
    }
}
