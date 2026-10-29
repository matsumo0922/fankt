package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineService
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor

/** The name the guest binds this service under and the host takes it by. */
internal const val FANBOX_GUEST_SERVICE_NAME: String = "fanboxGuest"

/**
 * Builds FANBOX requests and interprets FANBOX responses inside the Zipline guest, so that a
 * parsing fix reaches users by replacing the guest bundle rather than by shipping a new app.
 *
 * No parameter or return type carries a credential. The host attaches cookies and the CSRF token
 * after it validates the descriptor this service returns, and the guest observes neither.
 */
internal interface FanboxGuestService : ZiplineService {
    fun buildPostDetailRequest(postId: String): RequestDescriptor

    fun parsePostDetail(body: String, statusCode: Int): GuestParseResult
}

/**
 * The outcome of a guest-side response interpretation.
 *
 * Failures travel as a value because an exception crossing the Zipline bridge arrives as an opaque
 * type. The host parses the same response with its built-in parser whenever the result is not
 * [Success].
 */
@Serializable
internal sealed interface GuestParseResult {
    /** The response matched the schema the guest expects. */
    @Serializable
    data class Success(val postDetail: FanboxPostDetail) : GuestParseResult

    /** The guest's parser rejected the response. */
    @Serializable
    data class SchemaMismatch(val diagnostic: String) : GuestParseResult

    /** The guest failed for a reason other than the response. */
    @Serializable
    data class GuestFailure(val diagnostic: String) : GuestParseResult
}
