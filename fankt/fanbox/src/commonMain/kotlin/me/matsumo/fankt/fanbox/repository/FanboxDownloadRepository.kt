package me.matsumo.fankt.fanbox.repository

import io.ktor.client.plugins.onDownload
import io.ktor.client.statement.HttpStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.datasource.FanboxDownloadApi
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId

internal class FanboxDownloadRepository(
    private val fanboxDownloadApi: FanboxDownloadApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun downloadPostImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement = withContext(ioDispatcher) {
        fanboxDownloadApi.downloadPostImage(postId.value, itemId.value) {
            onDownload { bytesSentTotal, contentLength ->
                onDownload.invoke(contentLength?.let { bytesSentTotal.toFloat() / it } ?: 0f)
            }
        }
    }

    suspend fun downloadPostThumbnailImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement = withContext(ioDispatcher) {
        fanboxDownloadApi.downloadPostThumbnailImage(postId.value, itemId.value) {
            onDownload { bytesSentTotal, contentLength ->
                onDownload.invoke(contentLength?.let { bytesSentTotal.toFloat() / it } ?: 0f)
            }
        }
    }
}
