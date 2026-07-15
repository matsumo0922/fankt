package me.matsumo.fankt.fanbox.fixture

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.createFanboxJson

internal val goldenFixtureJson: Json = createFanboxJson()

internal inline fun <reified T> decodeFixture(json: String): T = goldenFixtureJson.decodeFromString(json)
