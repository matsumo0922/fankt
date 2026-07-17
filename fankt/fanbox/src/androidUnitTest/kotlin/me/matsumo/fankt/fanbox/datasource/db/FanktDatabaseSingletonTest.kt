package me.matsumo.fankt.fanbox.datasource.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.FanktInitializer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class FanktDatabaseSingletonTest {

    @Test
    fun concurrentAndRepeatedAccessReturnsSameDatabase() = runBlocking {
        FanktInitializer().create(ApplicationProvider.getApplicationContext<Context>())

        val workerCount = 8
        val ready = Channel<Unit>(workerCount)
        val start = CompletableDeferred<Unit>()
        val accesses = List(workerCount) {
            async(Dispatchers.Default) {
                ready.send(Unit)
                start.await()
                getFanktDatabase()
            }
        }

        repeat(workerCount) { ready.receive() }
        start.complete(Unit)

        val database = accesses.first().await()
        accesses.drop(1).forEach { access -> assertSame(database, access.await()) }
        assertSame(database, getFanktDatabase())
    }
}
