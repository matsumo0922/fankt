package me.matsumo.fankt.fanbox

import android.content.Context
import app.cash.zipline.loader.ZiplineCache
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/** [directory] 配下に guest bundle のキャッシュを置く。 */
fun FanboxGuestCache(context: Context, directory: File): FanboxGuestCache {
    val applicationContext = context.applicationContext

    return FanboxGuestCache { directoryName, maxSizeInBytes ->
        ZiplineCache(
            context = applicationContext,
            fileSystem = FileSystem.SYSTEM,
            directory = directory.toOkioPath() / directoryName,
            maxSizeInBytes = maxSizeInBytes,
        )
    }
}
