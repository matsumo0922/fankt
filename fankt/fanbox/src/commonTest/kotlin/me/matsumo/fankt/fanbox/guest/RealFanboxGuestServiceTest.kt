package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import me.matsumo.fankt.fanbox.response.FanboxResponses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The host turns these three outcomes into what the caller sees, so a body landing in the wrong one
 * is not visible as a failure — it changes the exception a caller handles.
 */
class RealFanboxGuestServiceTest {

    private val service = RealFanboxGuestService()

    @Test
    fun wellFormedResponseParses() {
        val result = service.parsePostDetail(FanboxPostJsonFixtures.postInfoArticleA, statusCode = 200)

        assertTrue(result is GuestParseResult.Success)
    }

    @Test
    fun changedSchemaIsAMismatchRatherThanAGuestFailure() {
        val result = service.parsePostDetail("""{"body":{"unexpected":true}}""", statusCode = 200)

        // GuestFailure here would report every FANBOX change as a broken guest rather than as the
        // schema change it is.
        assertTrue(result is GuestParseResult.SchemaMismatch)
    }

    @Test
    fun nonJsonBodyIsAMismatchRatherThanAGuestFailure() {
        val result = service.parsePostDetail("<html>maintenance</html>", statusCode = 200)

        assertTrue(result is GuestParseResult.SchemaMismatch)
    }

    @Test
    fun everyRequestIsTheOneTheEndpointBuilderMakes() {
        val cursor = GuestOperationCases.cursor

        assertEquals(
            listOf(
                FanboxEndpoints.homePosts(null),
                FanboxEndpoints.homePosts(cursor),
                FanboxEndpoints.supportingPosts(cursor),
                FanboxEndpoints.creatorPosts(FanboxCreatorId("creator"), cursor),
                FanboxEndpoints.creatorPostsPagination(FanboxCreatorId("creator")),
                FanboxEndpoints.postDetail(FanboxPostId("post")),
                FanboxEndpoints.postComments(FanboxPostId("post"), 20),
                FanboxEndpoints.taggedPosts("fixture", FanboxCreatorId("creator"), 2),
                FanboxEndpoints.taggedPosts("fixture", null, 0),
                FanboxEndpoints.likePost(FanboxPostId("post")),
                FanboxEndpoints.likeComment(FanboxCommentId("comment")),
                FanboxEndpoints.addComment(
                    postId = FanboxPostId("post"),
                    rootCommentId = FanboxCommentId("root"),
                    parentCommentId = FanboxCommentId("parent"),
                    body = "fixture comment",
                ),
                FanboxEndpoints.addComment(
                    postId = FanboxPostId("post"),
                    rootCommentId = null,
                    parentCommentId = null,
                    body = "fixture comment",
                ),
                FanboxEndpoints.deleteComment(FanboxCommentId("comment")),
                FanboxEndpoints.creatorDetail(FanboxCreatorId("creator")),
                FanboxEndpoints.followingCreators(),
                FanboxEndpoints.followingPixivCreators(),
                FanboxEndpoints.recommendedCreators(),
                FanboxEndpoints.creatorPlans(FanboxCreatorId("creator")),
                FanboxEndpoints.creatorPlanDetail(FanboxCreatorId("creator")),
                FanboxEndpoints.creatorTags(FanboxCreatorId("creator")),
                FanboxEndpoints.followCreator(FanboxUserId(91000001)),
                FanboxEndpoints.unfollowCreator(FanboxUserId(91000001)),
                FanboxEndpoints.searchCreators("fixture", 2),
                FanboxEndpoints.searchTags("fixture"),
            ),
            GuestOperationCases.buildRequests(service),
        )
    }

    @Test
    fun everyResponseIsWhatTheResponseParserReturns() {
        assertEquals(
            listOf(
                FanboxResponses.homePosts(FanboxTolerantListJsonFixtures.timelineMixed),
                FanboxResponses.supportingPosts(FanboxTolerantListJsonFixtures.timelineMixed),
                FanboxResponses.creatorPosts(FanboxPostJsonFixtures.postListCreatorNormal, GuestOperationCases.cursor),
                FanboxResponses.creatorPostsPagination(FanboxPostJsonFixtures.paginateCreatorNormal),
                FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleEmbeds),
                FanboxResponses.postComments(FanboxTolerantListJsonFixtures.commentsWithBrokenReply),
                FanboxResponses.taggedPosts(GuestOperationCases.taggedPosts),
                FanboxResponses.creatorDetail(FanboxCreatorJsonFixtures.actualCreatorGet),
                FanboxResponses.followingCreators(FanboxTolerantListJsonFixtures.creatorsMixed),
                FanboxResponses.followingPixivCreators(FanboxTolerantListJsonFixtures.creatorsMixed),
                FanboxResponses.recommendedCreators(FanboxTolerantListJsonFixtures.creatorsMixed),
                FanboxResponses.creatorPlans(FanboxCreatorJsonFixtures.actualCreatorPlanList),
                FanboxResponses.creatorPlanDetail(GuestOperationCases.supportedPlanDetail),
                FanboxResponses.creatorTags(GuestOperationCases.FEATURED_TAGS),
                FanboxResponses.searchCreators(GuestOperationCases.searchedCreators),
                FanboxResponses.searchTags(GuestOperationCases.SEARCHED_TAGS),
            ).map { value -> GuestParseResult.Success(value) },
            GuestOperationCases.parseResponses(service),
        )
    }

    @Test
    fun everyParserReportsARejectedResponseAsAMismatch() {
        GuestOperationCases.parseRejectedResponses(service).forEach { result ->
            assertIs<GuestParseResult.SchemaMismatch>(result)
        }
    }

    @Test
    fun descriptorCarriesTheRequestedPost() {
        val descriptor = service.buildPostDetailRequest("12345")

        assertEquals("post.info", descriptor.path)
        assertTrue(descriptor.query.any { it.name == "postId" && it.value == "12345" })
    }
}
