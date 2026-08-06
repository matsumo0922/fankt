package me.matsumo.fankt.fanbox.guest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wire schema of the types that cross the Zipline bridge by value.
 *
 * `ziplineApiCheck` hashes only the names and declared types of the bridge functions, so renaming a
 * field or changing a sealed discriminator leaves it passing while an older host can no longer
 * decode a newer bundle. That failure stays invisible until a bundle is delivered, so it is pinned
 * here instead.
 *
 * The fixtures record serial names, element names and sealed subtypes rather than a serialized
 * value, because a value has to be chosen per subtype and would miss the ones left out.
 */
@OptIn(ExperimentalSerializationApi::class)
class FanboxGuestWireSchemaTest {

    @Test
    fun requestDescriptorSchemaIsUnchanged() {
        assertEquals(
            """
            |me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
            |  endpointId: me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
            |  path: kotlin.String
            |  method: me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
            |  query: kotlin.collections.ArrayList
            |  jsonBody: kotlin.String?
            """.trimMargin(),
            describeElements(serializer<RequestDescriptor>().descriptor),
        )
    }

    @Test
    fun httpMethodConstantsAreUnchanged() {
        assertEquals(
            listOf("GET", "POST"),
            serializer<RequestDescriptor>().descriptor.elementNamed("method").elementNames(),
        )
    }

    @Test
    fun guestParseResultSubtypesAreUnchanged() {
        assertEquals(
            listOf(
                "me.matsumo.fankt.fanbox.guest.GuestParseResult.GuestFailure",
                "me.matsumo.fankt.fanbox.guest.GuestParseResult.SchemaMismatch",
                "me.matsumo.fankt.fanbox.guest.GuestParseResult.Success",
            ),
            subtypeNames(serializer<GuestParseResult>().descriptor),
        )
    }

    @Test
    fun postDetailElementNamesAreUnchanged() {
        assertEquals(
            listOf(
                "id",
                "title",
                "body",
                "coverImageUrl",
                "commentCount",
                "excerpt",
                "feeRequired",
                "hasAdultContent",
                "imageForShare",
                "isLiked",
                "isRestricted",
                "likeCount",
                "tags",
                "updatedDatetime",
                "publishedDatetime",
                "nextPost",
                "prevPost",
                "user",
            ),
            serializer<FanboxPostDetail>().descriptor.elementNames(),
        )
    }

    @Test
    fun postDetailBodySubtypesAreUnchanged() {
        assertEquals(
            listOf(
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.File",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Html",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Image",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Text",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Unknown",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Video",
            ),
            subtypeNames(serializer<FanboxPostDetail>().descriptor.elementNamed("body")),
        )
    }

    @Test
    fun articleBlockSubtypesAreUnchanged() {
        val blocks = serializer<FanboxPostDetail.Body.Article>().descriptor.elementNamed("blocks")

        assertEquals(
            listOf(
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Embed",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.File",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Image",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Link",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Text",
                "me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail.Body.Article.Block.Unknown",
            ),
            subtypeNames(blocks.getElementDescriptor(0)),
        )
    }

    private fun describeElements(descriptor: SerialDescriptor): String = buildString {
        append(descriptor.serialName)
        for (index in 0 until descriptor.elementsCount) {
            append("\n  ${descriptor.getElementName(index)}: ")
            append(descriptor.getElementDescriptor(index).serialName)
        }
    }

    /**
     * A sealed descriptor holds `element("type", …)` at index 0 and `element("value", …)` at index 1,
     * and the subtypes are the children of that second element (`SealedSerializer`). Their order
     * there follows the declaration order, which the wire never carries — only the discriminator
     * string in `type` does — so the names are sorted to keep the fixture from failing on a reorder.
     */
    private fun subtypeNames(descriptor: SerialDescriptor): List<String> {
        check(descriptor.kind is PolymorphicKind) { "${descriptor.serialName} is not polymorphic" }

        val value = descriptor.getElementDescriptor(1)

        return (0 until value.elementsCount)
            .map { index -> value.getElementDescriptor(index).serialName }
            .sorted()
    }
}

private fun SerialDescriptor.elementNames(): List<String> =
    (0 until elementsCount).map { index -> getElementName(index) }

private fun SerialDescriptor.elementNamed(name: String): SerialDescriptor =
    getElementDescriptor(getElementIndex(name))
