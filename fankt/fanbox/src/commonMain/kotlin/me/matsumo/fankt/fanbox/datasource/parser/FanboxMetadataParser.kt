package me.matsumo.fankt.fanbox.datasource.parser

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter

/** HTML 文字列から metadata JSON 文字列を取り出す関数型 */
internal fun interface FanboxMetadataExtractor {
    fun extract(html: String): String?
}

internal class FanboxMetadataParser(
    private val formatter: Json,
    private val extractor: FanboxMetadataExtractor,
) {
    fun parse(
        html: String,
        statusCode: Int? = null,
        endpoint: String = "homepage",
    ): FanboxMetaDataEntity {
        val metadata = extractor.extract(html)
            ?: throw FanboxFailureInterpreter.schemaMismatch(
                statusCode = statusCode,
                body = html,
                endpoint = endpoint,
                cause = null,
            )

        return try {
            formatter.decodeFromString(FanboxMetaDataEntity.serializer(), metadata)
        } catch (cause: SerializationException) {
            throw FanboxFailureInterpreter.schemaMismatch(
                statusCode = statusCode,
                body = html,
                endpoint = endpoint,
                cause = cause,
            )
        }
    }
}
