package me.matsumo.fankt.fanbox.datasource.parser

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.domain.entity.FanboxMetaDataEntity

internal class FanboxMetadataParser(
    private val formatter: Json,
) {
    fun parse(html: String): FanboxMetaDataEntity {
        val document = Ksoup.parse(html)
        val metadata = document.select("meta[name=metadata]").first()?.attr("content")

        return formatter.decodeFromString(FanboxMetaDataEntity.serializer(), metadata!!)
    }
}
