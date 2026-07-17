package me.matsumo.fankt.fanbox.datasource.mapper

import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPlanId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorMapperGoldenTest {

    @Test
    fun actualCreatorGetWithMultipleProfileItemsAndKnownAndUnknownLinksMapsFullObject() {
        val actual = FanboxCreatorMapper().map(
            decodeFixture<FanboxCreatorDetailEntity>(FanboxCreatorJsonFixtures.actualCreatorGet),
        )

        val expected = FanboxCreatorDetail(
            creatorId = FanboxCreatorId("fixture-creator-1"),
            coverImageUrl = "https://example.invalid/resource-2",
            description = "Fixture description 1",
            hasAdultContent = false,
            hasBoothShop = true,
            isAcceptingRequest = false,
            isFollowed = false,
            isStopped = false,
            isSupported = false,
            profileItems = listOf(
                FanboxCreatorDetail.ProfileItem(
                    id = "fixture-profile-item-1",
                    imageUrl = "https://example.invalid/resource-3",
                    thumbnailUrl = "https://example.invalid/resource-4",
                    type = "image",
                ),
                FanboxCreatorDetail.ProfileItem(
                    id = "fixture-profile-item-2",
                    imageUrl = "https://example.invalid/resource-5",
                    thumbnailUrl = "https://example.invalid/resource-6",
                    type = "image",
                ),
                FanboxCreatorDetail.ProfileItem(
                    id = "fixture-profile-item-3",
                    imageUrl = "https://example.invalid/resource-7",
                    thumbnailUrl = "https://example.invalid/resource-8",
                    type = "image",
                ),
            ),
            profileLinks = listOf(
                FanboxCreatorDetail.ProfileLink(
                    url = "https://www.youtube.com/fixture-known-1",
                    link = FanboxCreatorDetail.Platform.YOUTUBE,
                ),
                FanboxCreatorDetail.ProfileLink(
                    url = "https://example.invalid/fixture-unknown-2",
                    link = FanboxCreatorDetail.Platform.UNKNOWN,
                ),
            ),
            user = FanboxUser(
                userId = FanboxUserId(91000001),
                creatorId = FanboxCreatorId("fixture-creator-1"),
                name = "Fixture Name 1",
                iconUrl = "https://example.invalid/resource-1",
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun actualCreatorPlanListWithRealPlansAndNullablePaymentMethodMapsFullObjects() {
        val actual = FanboxCreatorMapper().map(
            decodeFixture<FanboxCreatorPlanListEntity>(FanboxCreatorJsonFixtures.actualCreatorPlanList),
            endpoint = "plan.listCreator",
        ).value

        val expected = listOf(
            FanboxCreatorPlan(
                id = FanboxPlanId("94000001"),
                title = "Fixture Title 1",
                description = "Fixture description 1",
                fee = 1234,
                coverImageUrl = "https://example.invalid/resource-1",
                hasAdultContent = false,
                paymentMethod = FanboxPaymentMethod.UNKNOWN,
                user = FanboxUser(
                    userId = FanboxUserId(91000001),
                    creatorId = FanboxCreatorId("fixture-creator-1"),
                    name = "Fixture Name 1",
                    iconUrl = "https://example.invalid/resource-2",
                ),
            ),
            FanboxCreatorPlan(
                id = FanboxPlanId("94000002"),
                title = "Fixture Title 2",
                description = "Fixture description 2",
                fee = 1234,
                coverImageUrl = "https://example.invalid/resource-3",
                hasAdultContent = false,
                paymentMethod = FanboxPaymentMethod.UNKNOWN,
                user = FanboxUser(
                    userId = FanboxUserId(91000002),
                    creatorId = FanboxCreatorId("fixture-creator-2"),
                    name = "Fixture Name 2",
                    iconUrl = "https://example.invalid/resource-4",
                ),
            ),
        )

        assertEquals(expected, actual)
    }
}
