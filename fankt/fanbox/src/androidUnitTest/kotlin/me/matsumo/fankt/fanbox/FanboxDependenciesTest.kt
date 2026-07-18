package me.matsumo.fankt.fanbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxDependenciesTest {

    @Test
    fun productionDependenciesIsolateDifferentStores() = runBlocking {
        val first = createFanboxDependencies(
            InMemoryFanboxCookieStorage(),
            InMemoryFanboxTokenStore(),
        )
        val second = createFanboxDependencies(
            InMemoryFanboxCookieStorage(),
            InMemoryFanboxTokenStore(),
        )

        first.setCsrfToken("first-token")

        assertEquals("first-token", first.getCsrfToken())
        assertEquals(null, second.getCsrfToken())
        assertEquals(null, second.csrfToken.first())
    }

    @Test
    fun productionDependenciesShareOnlyIdenticalStoreInstances() = runBlocking {
        val cookies = InMemoryFanboxCookieStorage()
        val tokens = InMemoryFanboxTokenStore()
        val first = createFanboxDependencies(cookies, tokens)
        val second = createFanboxDependencies(cookies, tokens)

        first.setCsrfToken("shared-token")

        assertEquals("shared-token", second.getCsrfToken())
        assertEquals("shared-token", second.csrfToken.first())
    }
}
