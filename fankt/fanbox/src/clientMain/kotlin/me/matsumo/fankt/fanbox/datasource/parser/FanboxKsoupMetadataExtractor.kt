package me.matsumo.fankt.fanbox.datasource.parser

import com.fleeksoft.ksoup.Ksoup

/** Ksoup で `meta[name=metadata]` の `content` を取り出す [FanboxMetadataExtractor] */
internal val FanboxKsoupMetadataExtractor = FanboxMetadataExtractor { html ->
    Ksoup.parse(html).select("meta[name=metadata]").first()?.attr("content")
}
