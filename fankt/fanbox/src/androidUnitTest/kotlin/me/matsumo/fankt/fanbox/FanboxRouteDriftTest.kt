package me.matsumo.fankt.fanbox

import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
import me.matsumo.fankt.fanbox.endpoint.fanboxRequestOperations
import me.matsumo.fankt.fanbox.endpoint.fanboxUniqueRequestRoutes
import me.matsumo.fankt.fanbox.response.fanboxResponseOperations
import me.matsumo.fankt.fanbox.transport.TrustedFanboxEndpointPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxRouteDriftTest {

    @Test
    fun productionInventoriesCoverEveryDescriptorPolicyAndResponseOperation() {
        val descriptors = productionDescriptors()
        val descriptorsById = descriptors.associateBy { it.endpointId }

        assertEquals(29, fanboxRequestOperations.size, "the request operation inventory changed")
        assertEquals(28, fanboxUniqueRequestRoutes.size, "the unique request route inventory changed")
        assertEquals(29, fanboxResponseOperations.size, "the response decode inventory changed")
        assertEquals(fanboxUniqueRequestRoutes, descriptorsById.keys)
        assertEquals(fanboxUniqueRequestRoutes, TrustedFanboxEndpointPolicy.entries.keys)

        descriptors.forEach { descriptor ->
            val policy = TrustedFanboxEndpointPolicy.resolve(descriptor.endpointId)
            assertEquals(descriptor.method, policy?.method, descriptor.endpointId.value)
            assertEquals(
                if (descriptor.endpointId == FanboxEndpointIds.homepage) {
                    TrustedFanboxEndpointPolicy.HOMEPAGE_ORIGIN
                } else {
                    TrustedFanboxEndpointPolicy.API_ORIGIN
                },
                policy?.origin,
                descriptor.endpointId.value,
            )
            assertEquals(
                descriptor.endpointId.value,
                descriptor.path.ifEmpty { "homepage" },
                "diagnostic label drifted for ${descriptor.endpointId.value}",
            )
        }

        assertEquals(
            fanboxRequestOperations.map { it.endpointId },
            fanboxResponseOperations.map { it.endpointId },
            "request operations and response decode operations must stay aligned",
        )
        assertEquals(
            fanboxRequestOperations.map { it.name },
            fanboxResponseOperations.map { it.name },
            "response parser diagnostic labels must stay aligned with request operations",
        )
    }

    @Test
    fun supportingPlanModesShareOneDescriptorAndTwoExplicitResponseOperations() {
        val strictDescriptor = FanboxEndpoints.supportingPlans()
        val tolerantDescriptor = FanboxEndpoints.supportingPlans()

        assertEquals(strictDescriptor, tolerantDescriptor)
        assertEquals(FanboxEndpointIds.planListSupporting, strictDescriptor.endpointId)
        assertEquals(FanboxHttpMethod.GET, strictDescriptor.method)
        assertEquals(
            listOf("plan.listSupporting.strict", "plan.listSupporting.tolerant"),
            fanboxResponseOperations
                .filter { it.endpointId == FanboxEndpointIds.planListSupporting }
                .map { it.name },
        )
        assertEquals(
            setOf(FanboxEndpointIds.planListSupporting),
            fanboxRequestOperations.groupBy { it.endpointId }.filterValues { it.size > 1 }.keys,
        )
    }

    private fun productionDescriptors() = listOf(
        FanboxEndpoints.homePosts(FanboxCursor(null, null, null, null, 20)),
        FanboxEndpoints.supportingPosts(null),
        FanboxEndpoints.creatorPosts(FanboxCreatorId("creator"), FanboxCursor(null, null, null, null, 20)),
        FanboxEndpoints.creatorPostsPagination(FanboxCreatorId("creator")),
        FanboxEndpoints.postDetail(FanboxPostId("post")),
        FanboxEndpoints.postComments(FanboxPostId("post"), 0),
        FanboxEndpoints.taggedPosts("tag", FanboxCreatorId("creator"), 0),
        FanboxEndpoints.likePost(FanboxPostId("post")),
        FanboxEndpoints.likeComment(FanboxCommentId("comment")),
        FanboxEndpoints.addComment(FanboxPostId("post"), null, null, "body"),
        FanboxEndpoints.deleteComment(FanboxCommentId("comment")),
        FanboxEndpoints.creatorDetail(FanboxCreatorId("creator")),
        FanboxEndpoints.followingCreators(),
        FanboxEndpoints.followingPixivCreators(),
        FanboxEndpoints.recommendedCreators(),
        FanboxEndpoints.creatorPlans(FanboxCreatorId("creator")),
        FanboxEndpoints.creatorPlanDetail(FanboxCreatorId("creator")),
        FanboxEndpoints.creatorTags(FanboxCreatorId("creator")),
        FanboxEndpoints.followCreator(FanboxUserId(1)),
        FanboxEndpoints.unfollowCreator(FanboxUserId(1)),
        FanboxEndpoints.searchCreators("query", 0),
        FanboxEndpoints.searchTags("query"),
        FanboxEndpoints.supportingPlans(),
        FanboxEndpoints.paidRecords(),
        FanboxEndpoints.unpaidRecords(),
        FanboxEndpoints.newsletters(),
        FanboxEndpoints.bells(0),
        FanboxEndpoints.homepage(),
    )
}
