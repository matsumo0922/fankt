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

    fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail>
}

/**
 * The outcome of a guest-side response interpretation.
 *
 * Failures travel as a value because an exception crossing the Zipline bridge arrives as an opaque
 * type.
 */
@Serializable
internal sealed interface GuestParseResult<out T> {
    /** The response matched the schema the guest expects. */
    @Serializable
    data class Success<out T>(val value: T) : GuestParseResult<T>

    /** The guest's parser rejected the response. */
    @Serializable
    data class SchemaMismatch(val diagnostic: String) : GuestParseResult<Nothing>

    /** The guest failed for a reason other than the response. */
    @Serializable
    data class GuestFailure(val diagnostic: String) : GuestParseResult<Nothing>
}
