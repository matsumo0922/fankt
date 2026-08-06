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
 * Guest failures travel as a value rather than as an exception because an exception crossing the
 * Zipline bridge arrives as an opaque type. Without the distinction below, a FANBOX schema change
 * and a broken guest engine become the same event: treating the former as the latter retreats to
 * the host-side path on every schema change, leaving the delivery path silently dead, while
 * treating the latter as the former reports an engine failure as a response problem.
 */
@Serializable
internal sealed interface GuestParseResult {
    /** The response matched the schema the guest expects. */
    @Serializable
    data class Success(val postDetail: FanboxPostDetail) : GuestParseResult

    /**
     * The response did not match the schema. The guest bundle is healthy; FANBOX changed. The host
     * reports this to its caller and keeps using the guest.
     */
    @Serializable
    data class SchemaMismatch(val diagnostic: String) : GuestParseResult

    /**
     * The guest itself failed. The host stops using the guest for the rest of this instance's life
     * rather than calling a broken engine once per request.
     */
    @Serializable
    data class GuestFailure(val diagnostic: String) : GuestParseResult
}
