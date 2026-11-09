package pl.ambif.ambify.core.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.ambif.ambify.shared.AuthPaths
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renewing the access token, against a small fake server that works like the real one: every refresh gives a new
 * refresh token and the old one stops working.
 */
class TokenRenewalTest {
    private lateinit var server: HttpServer
    private var baseUrl = ""

    @Volatile private var validAccess = "access-1"
    @Volatile private var validRefresh = "refresh-1"
    @Volatile private var refreshFails = false
    private val counter = AtomicInteger(1)
    private val refreshes = AtomicInteger(0)

    /** Like SharedPreferences("session"): one place shared by every ServerApi in the app. */
    private class SharedStore(var access: String?, var refresh: String?) {
        var expired = 0
    }

    private fun storeView(shared: SharedStore) = object : TokenStore {
        override var accessToken: String?
            get() = shared.access
            set(value) { shared.access = value }
        override var refreshToken: String?
            get() = shared.refresh
            set(value) { shared.refresh = value }
        override fun onSessionExpired() { shared.expired++ }
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(AuthPaths.REFRESH) { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            Thread.sleep(150) // wide enough for a second renewal to start meanwhile
            val (code, answer) = synchronized(this) {
                when {
                    refreshFails -> 502 to ""
                    body.contains("\"$validRefresh\"") -> {
                        refreshes.incrementAndGet()
                        val n = counter.incrementAndGet()
                        validAccess = "access-$n"
                        validRefresh = "refresh-$n"
                        200 to """{"accessToken":"$validAccess","refreshToken":"$validRefresh"}"""
                    }
                    else -> 401 to """{"code":"session_expired","message":"Sesja wygasła."}"""
                }
            }
            val bytes = answer.toByteArray()
            exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.createContext(AuthPaths.LOGOUT) { exchange ->
            val ok = exchange.requestHeaders.getFirst("Authorization") == "Bearer $validAccess"
            exchange.sendResponseHeaders(if (ok) 204 else 401, -1)
            exchange.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    /** The phone of a child: the screen and the background sending use two separate ServerApi at once. */
    @Test
    fun twoConnectionsRenewingAtOnceKeepTheSession() = runBlocking {
        val shared = SharedStore(access = "expired", refresh = validRefresh)
        val screen = ServerApi(baseUrl, storeView(shared))
        val background = ServerApi(baseUrl, storeView(shared))

        listOf(screen, background).map { api -> async(Dispatchers.IO) { api.logout() } }.awaitAll()

        assertEquals("nobody may be signed out", 0, shared.expired)
        assertEquals("one renewal is enough", 1, refreshes.get())
        assertEquals(validRefresh, shared.refresh)
    }

    @Test
    fun serverUnavailableDuringRenewalKeepsTheSession() = runBlocking {
        val shared = SharedStore(access = "expired", refresh = validRefresh)
        refreshFails = true

        val error = runCatching { ServerApi(baseUrl, storeView(shared)).logout() }.exceptionOrNull()

        assertTrue(error is ServerError && error.code == "network")
        assertEquals(0, shared.expired)
        assertEquals("refresh-1", shared.refresh)
    }

    @Test
    fun rejectedRefreshTokenEndsTheSession() = runBlocking {
        val shared = SharedStore(access = "expired", refresh = "revoked")

        val failed = runCatching { ServerApi(baseUrl, storeView(shared)).logout() }.isFailure

        assertTrue(failed)
        assertEquals(1, shared.expired)
        assertFalse(shared.refresh == validRefresh)
    }
}
