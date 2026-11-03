package me.matsumo.fankt.fanbox.guest

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.pthread_get_stacksize_np
import platform.posix.pthread_self
import kotlin.test.Test
import kotlin.test.assertTrue

/** Zipline を動かすスレッドが、Zipline の求める大きさのスタックを持つことを確かめる。 */
class ZiplineDispatcherTest {

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun theThreadHasTheStackZiplineExpects() = runBlocking {
        val stackSize = newZiplineDispatcher().use { dispatcher ->
            withContext(dispatcher) { pthread_get_stacksize_np(pthread_self()) }
        }

        assertTrue(stackSize >= ZIPLINE_THREAD_STACK_SIZE.toULong(), "stack size: $stackSize")
    }
}
