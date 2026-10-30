package me.matsumo.fankt.fanbox.transport

import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod

internal enum class FanboxCredentialBehavior {
    SHARED_FANBOX_AUTHENTICATION,
}

internal data class TrustedFanboxEndpoint(
    val origin: String,
    val method: FanboxHttpMethod,
    val credentialBehavior: FanboxCredentialBehavior,
)

internal object TrustedFanboxEndpointPolicy {
    const val API_ORIGIN = "https://api.fanbox.cc"
    const val HOMEPAGE_ORIGIN = "https://www.fanbox.cc"

    val entries: Map<FanboxEndpointId, TrustedFanboxEndpoint> = mapOf(
        FanboxEndpointIds.bellList to apiGet(),
        FanboxEndpointIds.creatorGet to apiGet(),
        FanboxEndpointIds.creatorListFollowing to apiGet(),
        FanboxEndpointIds.creatorListPixiv to apiGet(),
        FanboxEndpointIds.creatorListRecommended to apiGet(),
        FanboxEndpointIds.creatorSearch to apiGet(),
        FanboxEndpointIds.followCreate to apiPost(),
        FanboxEndpointIds.followDelete to apiPost(),
        FanboxEndpointIds.homepage to trusted(HOMEPAGE_ORIGIN, FanboxHttpMethod.GET),
        FanboxEndpointIds.legacySupportCreator to apiGet(),
        FanboxEndpointIds.newsletterList to apiGet(),
        FanboxEndpointIds.paymentListPaid to apiGet(),
        FanboxEndpointIds.paymentListUnpaid to apiGet(),
        FanboxEndpointIds.planListCreator to apiGet(),
        FanboxEndpointIds.planListSupporting to apiGet(),
        FanboxEndpointIds.postAddComment to apiPost(),
        FanboxEndpointIds.postDeleteComment to apiPost(),
        FanboxEndpointIds.postGetComments to apiGet(),
        FanboxEndpointIds.postInfo to apiGet(),
        FanboxEndpointIds.postLikeComment to apiPost(),
        FanboxEndpointIds.postLikePost to apiPost(),
        FanboxEndpointIds.postListCreator to apiGet(),
        FanboxEndpointIds.postListHome to apiGet(),
        FanboxEndpointIds.postListSupporting to apiGet(),
        FanboxEndpointIds.postListTagged to apiGet(),
        FanboxEndpointIds.postPaginateCreator to apiGet(),
        FanboxEndpointIds.tagGetFeatured to apiGet(),
        FanboxEndpointIds.tagSearch to apiGet(),
    )

    fun resolve(endpointId: FanboxEndpointId): TrustedFanboxEndpoint? = entries[endpointId]

    private fun apiGet() = trusted(API_ORIGIN, FanboxHttpMethod.GET)

    private fun apiPost() = trusted(API_ORIGIN, FanboxHttpMethod.POST)

    private fun trusted(origin: String, method: FanboxHttpMethod) = TrustedFanboxEndpoint(
        origin = origin,
        method = method,
        credentialBehavior = FanboxCredentialBehavior.SHARED_FANBOX_AUTHENTICATION,
    )
}
