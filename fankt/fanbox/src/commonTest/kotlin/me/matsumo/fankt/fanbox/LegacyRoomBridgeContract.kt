package me.matsumo.fankt.fanbox

import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.datasource.db.deleteLegacyRoomDatabaseFiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalLegacyFanboxStorageApi::class)
internal fun verifyLegacyRoomBridgeVersions(createFixture: (Int) -> Unit) = runBlocking {
    for (version in 1..3) {
        assertTrue(deleteLegacyRoomDatabaseFiles())
        createFixture(version)

        val first = createLegacyRoomFanboxCookieStorage()
        val snapshot = first.snapshot()
        assertEquals("legacy-session-v$version", snapshot.single().value)
        assertFalse(snapshot.single().hostOnly)
        assertEquals(
            listOf("FANBOXSESSID"),
            FanboxCookiesStorageAdapter(first)
                .get(Url("https://api.fanbox.cc/"))
                .map { it.name },
        )
        first.upsert(snapshot.single().copy(name = "coerced", hostOnly = true))
        assertFalse(first.snapshot().single { it.name == "coerced" }.hostOnly)
        first.close()

        val reacquired = createLegacyRoomFanboxCookieStorage()
        assertEquals(2, reacquired.snapshot().size)
        reacquired.clear()
        assertTrue(reacquired.snapshot().isEmpty())
        reacquired.close()
        assertTrue(reacquired.deleteDatabaseFiles())
    }
}
