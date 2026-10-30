package me.matsumo.fankt.fanbox

import kotlinx.coroutines.CoroutineDispatcher
import me.matsumo.fankt.fanbox.guest.LoadedGuest
import me.matsumo.fankt.fanbox.guest.RealFanboxGuestService

/** 配信 bundle の代わりに、guest の実装を Zipline なしでロードする。公開 API のテストで使う。 */
internal val nativeGuest: suspend (CoroutineDispatcher) -> LoadedGuest = { LoadedGuest(RealFanboxGuestService()) }
