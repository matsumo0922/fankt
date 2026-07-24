package me.matsumo.fankt.fanbox.datasource.parser

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter

internal class FanboxMetadataParser(
    private val formatter: Json,
) {
    fun parse(
        html: String,
        statusCode: Int? = null,
        endpoint: String = "homepage",
    ): FanboxMetaDataEntity {
        val document = Ksoup.parse(html)
        val metadata = document.select("meta[name=metadata]").first()?.attr("content")
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
