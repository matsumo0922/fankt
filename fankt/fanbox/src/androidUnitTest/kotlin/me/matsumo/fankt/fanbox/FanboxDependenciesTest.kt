package me.matsumo.fankt.fanbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class FanboxDependenciesTest {

    @Test
    fun productionDependenciesShareProcessCsrfTokenState() = runBlocking {
        FanktInitializer().create(ApplicationProvider.getApplicationContext<Context>())
        val first = createFanboxDependencies(Dispatchers.Unconfined)
        val second = createFanboxDependencies(Dispatchers.Unconfined)
        first.clearCsrfToken()

        first.setCsrfToken("shared-token")

        assertEquals("shared-token", first.getCsrfToken())
        assertEquals("shared-token", second.getCsrfToken())
        assertEquals("shared-token", second.csrfToken.first())
        second.clearCsrfToken()
        assertEquals(null, first.csrfToken.first())
    }
}
