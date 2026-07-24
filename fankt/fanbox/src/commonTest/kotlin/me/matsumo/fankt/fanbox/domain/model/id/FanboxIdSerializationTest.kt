package me.matsumo.fankt.fanbox.domain.model.id

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.fankt.fanbox.createFanboxJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that every FANBOX domain ID value class serializes as its underlying primitive
 * (a JSON string for the String-based IDs, a JSON number for [FanboxUserId]) rather than as a
 * wrapped JSON object, and that each round-trips through encode/decode unchanged.
 */
class FanboxIdSerializationTest {

    private val formatter = createFanboxJson()

    @Test
    fun commentIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxCommentId("comment-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxCommentId>(encoded)

        assertTrue(encodedElement.isString, "FanboxCommentId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun creatorIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxCreatorId("creator-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxCreatorId>(encoded)

        assertTrue(encodedElement.isString, "FanboxCreatorId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun newsLetterIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxNewsLetterId("news-letter-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxNewsLetterId>(encoded)

        assertTrue(encodedElement.isString, "FanboxNewsLetterId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun planIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxPlanId("plan-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxPlanId>(encoded)

        assertTrue(encodedElement.isString, "FanboxPlanId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun postIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxPostId("post-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxPostId>(encoded)

        assertTrue(encodedElement.isString, "FanboxPostId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun postItemIdSerializesAsJsonStringAndRoundTrips() {
        val expected = FanboxPostItemId("post-item-id")

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxPostItemId>(encoded)

        assertTrue(encodedElement.isString, "FanboxPostItemId must serialize as a JSON string")
        assertEquals(expected.value, encodedElement.content)
        assertEquals(expected, actual)
    }

    @Test
    fun userIdSerializesAsJsonNumberAndRoundTrips() {
        val expected = FanboxUserId(1234567890123)

        val encoded = formatter.encodeToString(expected)
        val encodedElement = formatter.parseToJsonElement(encoded).jsonPrimitive
        val actual = formatter.decodeFromString<FanboxUserId>(encoded)

        assertFalse(encodedElement.isString, "FanboxUserId must serialize as a JSON number, not a JSON string")
        assertEquals(expected.value.toString(), encodedElement.content)
        assertEquals(expected, actual)
    }
}
