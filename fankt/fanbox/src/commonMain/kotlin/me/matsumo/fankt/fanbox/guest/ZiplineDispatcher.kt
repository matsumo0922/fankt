package me.matsumo.fankt.fanbox.guest

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Zipline を動かすスレッドのスタックの大きさ。
 *
 * Zipline は QuickJS のスタック上限を 6 MiB に設定し、呼び出し側のスレッドに 8 MiB のスタックを求める。
 * QuickJS の上限より先にスレッドのスタックが尽きると、JS の例外にならずにプロセスごと落ちる。
 */
internal const val ZIPLINE_THREAD_STACK_SIZE = 8L * 1024 * 1024

/** Zipline を動かす単一スレッドの dispatcher を生成する。スレッドのスタックは [ZIPLINE_THREAD_STACK_SIZE] にする。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal expect fun newZiplineDispatcher(): CloseableCoroutineDispatcher
