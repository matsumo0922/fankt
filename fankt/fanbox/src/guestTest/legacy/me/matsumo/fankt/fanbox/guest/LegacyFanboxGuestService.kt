package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxResponses

internal class LegacyFanboxGuestService : FanboxGuestService {

    override fun buildPostDetailRequest(postId: String): RequestDescriptor =
        FanboxEndpoints.postDetail(FanboxPostId(postId))

    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult = try {
        val legacyPost = createFanboxJson().parseToJsonElement(body).jsonObject.getValue("body")
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
