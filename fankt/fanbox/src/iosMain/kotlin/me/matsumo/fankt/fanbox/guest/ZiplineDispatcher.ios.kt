package me.matsumo.fankt.fanbox.guest

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSThread
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

// newSingleThreadContext cannot size the stack of its thread, which is 512 KiB on iOS.
// So the thread is started here with the size set, and runs the event loop of runBlocking until closed.
@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun newZiplineDispatcher(): CloseableCoroutineDispatcher {
    val eventLoop = CompletableDeferred<CoroutineDispatcher>()
    val stop = CompletableDeferred<Unit>()
    val thread = NSThread {
        runBlocking {
            eventLoop.complete(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
            stop.await()
        }
    }

    thread.name = "FanboxZipline"
    thread.stackSize = ZIPLINE_THREAD_STACK_SIZE.toULong()
    thread.start()

    return ThreadEventLoopDispatcher(
        eventLoop = runBlocking { eventLoop.await() },
        stop = stop,
    )
}

/** [eventLoop] へ処理を渡す dispatcher。閉じると、[eventLoop] を動かすスレッドは残りの処理を終えてから終了する。 */
@OptIn(ExperimentalCoroutinesApi::class)
private class ThreadEventLoopDispatcher(
    private val eventLoop: CoroutineDispatcher,
    private val stop: CompletableDeferred<Unit>,
) : CloseableCoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // The event loop of a finished runBlocking hands its tasks to another thread, whose stack is too small for Zipline.
        check(!stop.isCompleted) { "The FanboxZipline dispatcher is closed" }

        eventLoop.dispatch(context, block)
    }

    override fun close() {
        stop.complete(Unit)
    }
}
