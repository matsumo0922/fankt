package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.response.FanboxResponses

/** post.info の response を旧 envelope として解釈する guest。それ以外の operation は現行の guest と同じ。 */
internal class LegacyFanboxGuestService : FanboxGuestService by RealFanboxGuestService() {

    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail> = try {
        val legacyPost = createFanboxJson()
            .parseToJsonElement(body).jsonObject
            .getValue("body")
        val normalized = buildJsonObject {
            put("body", buildJsonObject { put("post", legacyPost) })
        }
        GuestParseResult.Success(FanboxResponses.postDetail(normalized.toString(), statusCode))
    } catch (failure: FanboxException) {
        GuestParseResult.SchemaMismatch(failure.message.orEmpty())
    } catch (failure: Throwable) {
        GuestParseResult.GuestFailure(failure.message.orEmpty())
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun launchZipline() {
    Zipline.get().bind<FanboxGuestService>(
        name = FANBOX_GUEST_SERVICE_NAME,
        instance = LegacyFanboxGuestService(),
    )
}
