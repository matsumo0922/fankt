package me.matsumo.fankt.fanbox.guest

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun newZiplineDispatcher(): CloseableCoroutineDispatcher = Executors
    .newSingleThreadExecutor { task ->
        Thread(null, task, "FanboxZipline", ZIPLINE_THREAD_STACK_SIZE).apply { isDaemon = true }
    }
    .asCoroutineDispatcher()
