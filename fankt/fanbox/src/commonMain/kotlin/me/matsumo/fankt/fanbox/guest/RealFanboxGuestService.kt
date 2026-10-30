package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxResponses

/** guest bundle が bind する [FanboxGuestService] の実装。FanboxEndpoints と FanboxResponses を bridge へ載せる。 */
internal class RealFanboxGuestService : FanboxGuestService {

    override fun buildPostDetailRequest(postId: String): RequestDescriptor = FanboxEndpoints.postDetail(FanboxPostId(postId))

    // Throwable rather than RuntimeException: a Kotlin/JS engine failure need not be one.
    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail> = try {
        GuestParseResult.Success(FanboxResponses.postDetail(body, statusCode))
    } catch (failure: FanboxException) {
        GuestParseResult.SchemaMismatch(failure.describe())
    } catch (failure: Throwable) {
        GuestParseResult.GuestFailure(failure.describe())
    }

    private fun Throwable.describe(): String = message?.takeIf(String::isNotBlank) ?: this::class.simpleName.orEmpty()
}
