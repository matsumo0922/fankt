package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCreator
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.fixture.FanboxPaymentJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxUserMapperGoldenTest {

    @Test
    fun paidRecordsWithBodyPaymentsWrapperMapFullObjects() {
        val actual = FanboxUserMapper(
            postMapper = FanboxPostMapper(),
            creatorMapper = FanboxCreatorMapper(),
        ).map(
            decodeFixture<FanboxPaidRecordListEntity>(FanboxPaymentJsonFixtures.paidRecords),
        )

        val expected = listOf(
            FanboxPaidRecord(
                id = "fixture-paid-record-1",
                paidAmount = 987654321,
                paymentDateTime = Instant.parse("2000-01-01T00:00:00+00:00"),
                paymentMethod = FanboxPaymentMethod.UNKNOWN,
                creator = FanboxCreator(
                    creatorId = FanboxCreatorId("fixture-paid-creator-1"),
                    user = FanboxUser(
                        userId = FanboxUserId(94000001),
                        creatorId = FanboxCreatorId("fixture-paid-creator-1"),
                        name = "Fixture Paid User 1",
                        iconUrl = "https://example.invalid/paid-user-1.png",
                    ),
                ),
            ),
        )

        assertEquals(expected, actual)
    }
}
