package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxBellReadStateTest {

    @Test
    fun listingNotificationsLeavesThemUnreadByDefault() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.getBells(page = 0)

            assertEquals("1", fixture.bellRequest().parameters["skipConvertUnreadNotification"])
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun listingNotificationsConvertsUnreadOnlyWhenRequested() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.getBells(page = 0, markNotificationsRead = true)

            assertEquals("0", fixture.bellRequest().parameters["skipConvertUnreadNotification"])
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun mismatchReportingRouteKeepsTheRequestedReadState() = runBlocking {
        val fixture = createFixture()
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            fixture.fanbox.getBells(page = 0, onItemSchemaMismatch = mismatches::add)
            assertEquals("1", fixture.bellRequest().parameters["skipConvertUnreadNotification"])

            fixture.fanbox.getBells(
                page = 0,
                onItemSchemaMismatch = mismatches::add,
                markNotificationsRead = true,
            )
            assertEquals("0", fixture.requestUrls.last().parameters["skipConvertUnreadNotification"])
            assertEquals(
                listOf(
                    FanboxListItemSchemaMismatch("bell.list", listOf(1)),
                    FanboxListItemSchemaMismatch("bell.list", listOf(1)),
                ),
                mismatches,
            )
        } finally {
            fixture.fanbox.close()
        }
    }

    private fun createFixture(): Fixture {
        val requestUrls = mutableListOf<Url>()
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requestUrls += request.url
                    respond(
                        content = FanboxTolerantListJsonFixtures.bellMixed,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                block,
            )
        }
        val csrfToken = MutableStateFlow<String?>(null)
        val fanbox = Fanbox(
            dependencies = FanboxDependencies(
                cookieStorage = AcceptAllCookiesStorage(),
                cookies = emptyFlow(),
                csrfToken = csrfToken,
                getCsrfToken = { csrfToken.value },
                setCsrfToken = { csrfToken.value = it },
                clearCsrfToken = { csrfToken.value = null },
                overrideFanboxSessionId = {},
                addCookie = {},
                replaceCookies = {},
            ),
            clientFactory = clientFactory,
            ioDispatcher = Dispatchers.Default,
        )
        return Fixture(fanbox, requestUrls)
    }

    private class Fixture(
        val fanbox: Fanbox,
        val requestUrls: MutableList<Url>,
    ) {
        fun bellRequest(): Url = requestUrls.single { it.encodedPath.endsWith("bell.list") }
    }
}
