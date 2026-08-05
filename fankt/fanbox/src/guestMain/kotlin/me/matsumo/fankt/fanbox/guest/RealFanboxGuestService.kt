package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxResponses

internal class RealFanboxGuestService : FanboxGuestService {

    override fun buildPostDetailRequest(postId: String): RequestDescriptor =
        FanboxEndpoints.postDetail(FanboxPostId(postId))

    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult = try {
        GuestParseResult.Success(FanboxResponses.postDetail(body, statusCode))
    } catch (failure: FanboxException.SchemaMismatch) {
        GuestParseResult.SchemaMismatch(failure.message.orEmpty())
    } catch (failure: FanboxException) {
        GuestParseResult.SchemaMismatch(failure.message.orEmpty())
    } catch (failure: RuntimeException) {
        GuestParseResult.GuestFailure(failure.message ?: failure::class.simpleName.orEmpty())
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun launchZipline() {
    Zipline.get().bind<FanboxGuestService>(
        name = FANBOX_GUEST_SERVICE_NAME,
        instance = RealFanboxGuestService(),
    )
}
