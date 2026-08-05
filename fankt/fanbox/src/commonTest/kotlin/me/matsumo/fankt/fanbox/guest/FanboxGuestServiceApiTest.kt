package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the promise that the guest cannot reach a credential.
 *
 * The bridge is the only channel between the guest and the host, so a credential can only reach
 * the guest as a parameter or a return value of a bridge function. Binding every such value to its
 * declared type here means adding a credential-carrying parameter stops compiling this file, and
 * `ziplineApiCheck` separately fails the build when a bridge function changes without updating
 * `api/zipline-api.toml`.
 */
class FanboxGuestServiceApiTest {

    @Test
    fun `bridge carries only identifiers and response text and parsed results`() {
        val service: FanboxGuestService = StubGuestService()

        // Compiles only while every bridge value is one of these types. A cookie store, a token
        // store, or a raw credential string in either position would fail this binding.
        val postId: String = "post_id"
        val descriptor: RequestDescriptor = service.buildPostDetailRequest(postId)
        val parsed: GuestParseResult = service.parsePostDetail(body = "{}", statusCode = 200)

        assertTrue(descriptor.path.isNotEmpty())
        assertTrue(parsed is GuestParseResult.SchemaMismatch)
    }

    @Test
    fun `parse result distinguishes a schema mismatch from a guest failure`() {
        val mismatch: GuestParseResult = GuestParseResult.SchemaMismatch("schema")
        val failure: GuestParseResult = GuestParseResult.GuestFailure("engine")

        // The host branches on these to decide whether to keep using the guest, so they must not
        // collapse into one another.
        assertTrue(mismatch !is GuestParseResult.GuestFailure)
        assertTrue(failure !is GuestParseResult.SchemaMismatch)
    }

    private class StubGuestService : FanboxGuestService {
        override fun buildPostDetailRequest(postId: String): RequestDescriptor =
            FanboxEndpoints.postDetail(FanboxPostId(postId))

        override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult =
            GuestParseResult.SchemaMismatch("stub")
    }
}
