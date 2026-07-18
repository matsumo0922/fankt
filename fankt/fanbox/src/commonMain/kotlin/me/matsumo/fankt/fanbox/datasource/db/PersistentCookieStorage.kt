package me.matsumo.fankt.fanbox.datasource.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxCookieStorage
import me.matsumo.fankt.fanbox.canonicalDomain
import me.matsumo.fankt.fanbox.canonicalPath
import me.matsumo.fankt.fanbox.domain.model.db.CookieEntity

internal class PersistentCookieStorage(
    private val cookieDao: CookieDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FanboxCookieStorage {
    override val cookies: Flow<List<FanboxCookieRecord>> =
        cookieDao.getAllCookies().map { entities -> entities.map(CookieEntity::toRecord) }

    override suspend fun snapshot(): List<FanboxCookieRecord> = withContext(ioDispatcher) {
        cookieDao.getAllCookies().first().map(CookieEntity::toRecord)
    }

    override suspend fun upsert(cookie: FanboxCookieRecord) {
        withContext(ioDispatcher) {
            cookieDao.insert(cookie.toLegacyEntity())
        }
    }

    override suspend fun delete(domain: String, path: String, name: String) {
        withContext(ioDispatcher) {
            cookieDao.delete(domain.canonicalDomain(), path.canonicalPath(), name)
        }
    }

    override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
        withContext(ioDispatcher) {
            cookieDao.deleteExpired(nowEpochMilliseconds)
        }
    }

    override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) {
        val replacement = cookies.map(FanboxCookieRecord::toLegacyEntity)
        withContext(ioDispatcher) {
            cookieDao.replaceAll(replacement)
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            cookieDao.clear()
        }
    }
}

private fun CookieEntity.toRecord(): FanboxCookieRecord = FanboxCookieRecord(
    domain = domain.canonicalDomain(),
    path = path.canonicalPath(),
    name = name,
    value = value,
    expiresAtEpochMilliseconds = expiresAt,
    secure = secure,
    hostOnly = false,
)

private fun FanboxCookieRecord.toLegacyEntity(): CookieEntity = CookieEntity(
    domain = domain.canonicalDomain(),
    path = path.canonicalPath(),
    name = name,
    value = value,
    expiresAt = expiresAtEpochMilliseconds,
    secure = secure,
)
