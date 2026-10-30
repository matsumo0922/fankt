package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.FanboxGuestCache
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FanboxGuestDeliveryConfigTest {

    @Test
    fun theCacheDirectoryIgnoresTheOrderOfTheKeys() {
        val forward = config(linkedMapOf("primary" to KEY_A, "recovery" to KEY_B))
        val backward = config(linkedMapOf("recovery" to KEY_B, "primary" to KEY_A))

        assertEquals(forward.cacheDirectoryName, backward.cacheDirectoryName)
    }

    @Test
    fun anyChangeToTheKeysMovesTheCacheDirectory() {
        val original = config(mapOf("primary" to KEY_A, "recovery" to KEY_B))
        val changed = listOf(
            mapOf("primary" to KEY_A),
            mapOf("primary" to KEY_A, "recovery" to KEY_B, "next" to KEY_C),
            mapOf("primary" to KEY_C, "recovery" to KEY_B),
            mapOf("primary2" to KEY_A, "recovery" to KEY_B),
        )

        for (trustedKeys in changed) {
            assertNotEquals(original.cacheDirectoryName, config(trustedKeys).cacheDirectoryName)
        }
    }

    @Test
    fun aKeyNameBoundaryCannotBeShiftedIntoTheNextName() {
        // Without the length of each name, both sets would hash "a" + key + "b" + key.
        val printableKey = "k".repeat(32)
        val twoKeys = config(mapOf("a" to printableKey.encodeUtf8(), "b" to printableKey.encodeUtf8()))
        val oneKey = config(mapOf("a${printableKey}b" to printableKey.encodeUtf8()))

        assertNotEquals(twoKeys.cacheDirectoryName, oneKey.cacheDirectoryName)
    }

    private fun config(trustedKeys: Map<String, ByteString>) = FanboxGuestDeliveryConfig(
        manifestUrl = "https://example.invalid/manifest.zipline.json",
        trustedKeys = trustedKeys,
        cache = FanboxGuestCache { _, _ -> error("the cache is not opened by these tests") },
    )

    private companion object {
        val KEY_A = ByteArray(32) { 1 }.toByteString()
        val KEY_B = ByteArray(32) { 2 }.toByteString()
        val KEY_C = ByteArray(32) { 3 }.toByteString()
    }
}
