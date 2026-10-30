package me.matsumo.fankt.fanbox

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * host が FANBOX の request を組み立てず、response も解析しないことを固定する。
 *
 * どちらも guest の中身である FanboxEndpoints と FanboxResponses が行う。host から直接呼ぶと、配信した guest を
 * 通らない経路ができ、その operation だけ OTA で直せなくなる。
 */
class FanboxGuestOnlyHostTest {

    @Test
    fun theHostReachesEndpointsAndResponsesOnlyThroughTheGuest() {
        // Unit tests run from the module directory.
        val sources = File("src/clientMain/kotlin")
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .toList()
        val reference = Regex("\\bFanbox(Endpoints|Responses)\\b")

        assertTrue(sources.isNotEmpty(), "no host source was found under ${File("").absolutePath}")
        assertEquals(
            emptyList(),
            sources.filter { source -> reference.containsMatchIn(source.readText()) }.map(File::getPath),
        )
    }
}
