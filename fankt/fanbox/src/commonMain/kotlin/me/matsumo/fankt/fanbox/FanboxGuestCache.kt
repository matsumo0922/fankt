package me.matsumo.fankt.fanbox

import app.cash.zipline.loader.ZiplineCache

/**
 * guest bundle のモジュールを保存するディスクキャッシュ。
 *
 * Android では `FanboxGuestCache(context, directory)`、iOS では `FanboxGuestCache(directory)` で生成する。
 * 1 つのディレクトリを複数の [Fanbox] で同時に使わない。
 */
class FanboxGuestCache internal constructor(
    internal val open: (directoryName: String, maxSizeInBytes: Long) -> ZiplineCache,
)
