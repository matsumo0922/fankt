package me.matsumo.fankt.fanbox.datasource.db

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxCookiesStorageAdapter
import me.matsumo.fankt.fanbox.domain.model.db.CookieEntity
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentCookieStorageTest {
    @Test
    fun legacyReadsAndWritesCoerceHostOnlyToFalse() = runBlocking {
        val dao = FakeCookieDao(cookie("existing", "value"))
        val storage = PersistentCookieStorage(dao)

        assertFalse(storage.snapshot().single().hostOnly)

        storage.upsert(record("new", "value", hostOnly = true))

        assertFalse(storage.snapshot().single { it.name == "new" }.hostOnly)
    }

    @Test
    fun replaceAllUsesOneDaoTransactionEntryPoint() = runBlocking {
        val dao = FakeCookieDao(cookie("old", "value"))
        val storage = PersistentCookieStorage(dao)

        storage.replaceAll(listOf(record("first", "value"), record("second", "value")))

        assertEquals(1, dao.replaceAllCalls)
        assertEquals(setOf("first", "second"), storage.snapshot().map { it.name }.toSet())
    }

    @Test
    fun fanboxRequestUsesCommonAdapterForLegacyBackend() = runBlocking {
        val dao = FakeCookieDao(
            cookie("fanbox", "fanbox-value"),
            cookie("pixiv", "pixiv-value", domain = "pixiv.net"),
            cookie("expired", "expired-value", expiresAt = NOW),
        )
        val storage = PersistentCookieStorage(dao)
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }

        val cookies = adapter.get(Url("https://api.fanbox.cc/post.info"))

        assertEquals(listOf("fanbox"), cookies.map { it.name })
        assertEquals(1, dao.deleteExpiredCalls)
        assertFalse(storage.snapshot().any { it.name == "expired" })
    }

    @Test
    fun backendOperationsEnterInjectedDispatcher() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val dao = FakeCookieDao()
        val storage = PersistentCookieStorage(dao, dispatcher)

        storage.upsert(record("session", "value"))

        assertTrue(dispatcher.dispatchCount > 0)
        assertTrue(dao.insertObservedDispatch)
    }

    private fun record(
        name: String,
        value: String,
        domain: String = "fanbox.cc",
        hostOnly: Boolean = false,
    ) = FanboxCookieRecord(domain, "/", name, value, null, secure = true, hostOnly)

    private fun cookie(
        name: String,
        value: String,
        domain: String = "fanbox.cc",
        expiresAt: Long? = null,
    ) = CookieEntity(domain, "/", name, value, expiresAt, secure = true)

    private class FakeCookieDao(vararg initialCookies: CookieEntity) : CookieDao {
        private val CookieEntity.identity get() = Triple(domain, path, name)

        val state = MutableStateFlow(initialCookies.toList())
        var replaceAllCalls = 0
        var deleteExpiredCalls = 0
        var insertObservedDispatch = false

        override fun getAllCookies(): Flow<List<CookieEntity>> = state

        override suspend fun insert(cookie: CookieEntity) {
            insertObservedDispatch = RecordingDispatcher.isDispatching
            state.value = state.value.filterNot { it.identity == cookie.identity } + cookie
        }

        override suspend fun insertAll(cookies: List<CookieEntity>) {
            cookies.forEach { insert(it) }
        }

        override suspend fun delete(domain: String, path: String, name: String) {
            state.value = state.value.filterNot { it.identity == Triple(domain, path, name) }
        }

        override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
            deleteExpiredCalls += 1
            state.value = state.value.filterNot {
                it.expiresAt?.let { expiry -> expiry <= nowEpochMilliseconds } == true
            }
        }

        override suspend fun clear() {
            state.value = emptyList()
        }

        override suspend fun replaceAll(cookies: List<CookieEntity>) {
            replaceAllCalls += 1
            state.value = cookies
        }
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount += 1
            isDispatching = true
            try {
                block.run()
            } finally {
                isDispatching = false
            }
        }

        companion object {
            var isDispatching = false
        }
    }

    private companion object {
        const val NOW = 1_750_000_000_000L
    }
}
