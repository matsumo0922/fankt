package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * FANBOX のレスポンスが `body` の下に持つラッパーの名前を固定するテスト。
 *
 * FANBOX は一覧系のレスポンスで `body` を裸の配列にせず、`{"body": {"plans": [...]}}` のように
 * 要素名を持つオブジェクトで包む。この階層を落とすと、対応する entity は HTTP 200 のまま
 * デコードに失敗し、`FanboxException.SchemaMismatch` になる。コンパイルは通り、fixture も
 * 同じ誤りを持っていればテストも通るため、実機で初めて発覚する。
 *
 * 実際のレスポンスから採った最小のエンベロープをリテラルで持ち、entity がそれを読めることと、
 * ラッパーを外した形を読めないことの両方を確認する。fixture 側の誤りに引きずられないよう、
 * entity の検査には fixture を使わない。fixture 自体の形は末尾の 3 件で別に検査する。
 */
class FanboxResponseEnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun creatorPlanListIsWrappedInPlans() {
        val entity = json.decodeFromString(
            FanboxCreatorPlanListEntity.serializer(),
            """{"body":{"plans":[{"id":"1","title":"t","fee":100,"description":"d","coverImageUrl":null,"user":null,"creatorId":"c","hasAdultContent":false,"paymentMethod":null}]}}""",
        )

        assertEquals(1, entity.body.plans.size)
    }

    @Test
    fun creatorPlanListRejectsABareArray() {
        assertFailsWith<Exception> {
            json.decodeFromString(FanboxCreatorPlanListEntity.serializer(), """{"body":[]}""")
        }
    }

    @Test
    fun strictPlanListIsWrappedInPlans() {
        val entity = json.decodeFromString(
            FanboxCreatorPlanListStrictEntity.serializer(),
            """{"body":{"plans":[{"id":"1","title":"t","fee":100,"description":"d","coverImageUrl":null,"user":null,"creatorId":"c","hasAdultContent":false,"paymentMethod":null}]}}""",
        )

        assertEquals(1, entity.body.plans.size)
    }

    @Test
    fun strictPlanListRejectsABareArray() {
        assertFailsWith<Exception> {
            json.decodeFromString(FanboxCreatorPlanListStrictEntity.serializer(), """{"body":[]}""")
        }
    }

    @Test
    fun creatorPostItemsAreWrappedInPosts() {
        val entity = json.decodeFromString(
            FanboxCreatorPostItemsEntity.serializer(),
            """{"body":{"posts":[{"id":"1"}]}}""",
        )

        assertEquals(1, entity.body.posts.size)
    }

    @Test
    fun creatorPostItemsRejectABareArray() {
        assertFailsWith<Exception> {
            json.decodeFromString(FanboxCreatorPostItemsEntity.serializer(), """{"body":[]}""")
        }
    }

    @Test
    fun paginateCreatorIsWrappedInPageUrls() {
        val entity = json.decodeFromString(
            FanboxCreatorPostsPaginateEntity.serializer(),
            """{"body":{"pageUrls":["https://example.invalid/1","https://example.invalid/2"]}}""",
        )

        assertEquals(2, entity.body.pageUrls.size)
    }

    @Test
    fun paginateCreatorRejectsABareArray() {
        assertFailsWith<Exception> {
            json.decodeFromString(FanboxCreatorPostsPaginateEntity.serializer(), """{"body":[]}""")
        }
    }

    /**
     * `body` が本当に裸の配列であるエンドポイントは、この規則の例外として残る。
     *
     * ここに挙がっていない一覧系の entity で `body` が配列になっている場合、ラッパーを落とした
     * 疑いがある。
     */
    @Test
    fun newsletterListIsGenuinelyABareArray() {
        val entity = json.decodeFromString(
            FanboxNewsLettersEntity.serializer(),
            """{"body":[]}""",
        )

        assertTrue(entity.body.isEmpty())
    }

    @Test
    fun tagListIsGenuinelyABareArray() {
        val entity = json.decodeFromString(
            FanboxTagListEntity.serializer(),
            """{"body":[{"count":1,"value":"v"}]}""",
        )

        assertEquals(1, entity.body.size)
    }

    /**
     * fixture が実際のレスポンスと同じエンベロープを持つことを確認する。
     *
     * 今回の不具合は fixture 側がラッパーを持たない形で作られ、その fixture に合わせて entity が
     * 書き換えられたことで起きた。fixture と entity が揃って誤っていると既存のテストは通るため、
     * fixture の形そのものを検査する。
     */
    @Test
    fun planListFixtureHasThePlansWrapper() {
        val body = json.parseToJsonElement(
            FanboxCreatorJsonFixtures.actualCreatorPlanList,
        ) as JsonObject

        val bodyObject = body.getValue("body")
        assertTrue(bodyObject is JsonObject, "body must be an object, not ${bodyObject::class.simpleName}")
        assertTrue(bodyObject.getValue("plans") is JsonArray)
    }

    @Test
    fun postListFixtureHasThePostsWrapper() {
        val body = json.parseToJsonElement(
            FanboxPostJsonFixtures.postListCreatorNormal,
        ) as JsonObject

        val bodyObject = body.getValue("body")
        assertTrue(bodyObject is JsonObject, "body must be an object, not ${bodyObject::class.simpleName}")
        assertTrue(bodyObject.getValue("posts") is JsonArray)
    }

    @Test
    fun paginateFixtureHasThePageUrlsWrapper() {
        val body = json.parseToJsonElement(
            FanboxPostJsonFixtures.paginateCreatorNormal,
        ) as JsonObject

        val bodyObject = body.getValue("body")
        assertTrue(bodyObject is JsonObject, "body must be an object, not ${bodyObject::class.simpleName}")
        assertTrue(bodyObject.getValue("pageUrls") is JsonArray)
    }
}
