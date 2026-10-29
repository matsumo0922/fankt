package me.matsumo.fankt.fanbox

import app.cash.zipline.loader.ZiplineCache
import okio.FileSystem
import okio.Path.Companion.toPath

/** [directory] 配下に guest bundle のキャッシュを置く。 */
fun FanboxGuestCache(directory: String): FanboxGuestCache = FanboxGuestCache { directoryName, maxSizeInBytes ->
    ZiplineCache(
        fileSystem = FileSystem.SYSTEM,
        directory = directory.toPath() / directoryName,
        maxSizeInBytes = maxSizeInBytes,
    )
}
