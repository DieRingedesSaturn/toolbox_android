package com.example.toolbox.fx

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FxRateReaderTest {

    /** Single socket server keyed on the request path. */
    private class FakeServer {
        private val serverSocket = ServerSocket()
        private val responses = ConcurrentHashMap<String, Pair<Int, String>>()
        val requestedPaths = CopyOnWriteArrayList<String>()
        val baseUrl: String

        @Volatile
        private var running = true

        init {
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
            thread(name = "fake-fx") {
                while (running) {
                    val client = try {
                        serverSocket.accept()
                    } catch (e: SocketException) {
                        break
                    }
                    runCatching { handle(client) }
                }
            }
        }

        fun respond(path: String, code: Int = 200, body: String) {
            responses[path] = code to body
        }

        fun stop() {
            running = false
            serverSocket.close()
        }

        private fun handle(client: Socket) {
            client.use { socket ->
                val input = socket.getInputStream()
                val head = StringBuilder()
                while (true) {
                    val byte = input.read()
                    if (byte < 0 || head.length > 64 * 1024) return
                    head.append(byte.toChar())
                    if (head.endsWith("\r\n\r\n")) break
                }
                val path = head.toString().split("\r\n").firstOrNull()
                    ?.split(" ")?.getOrNull(1) ?: "/"
                requestedPaths += path
                val (code, body) = responses[path] ?: (404 to "")
                val bytes = body.toByteArray(Charsets.UTF_8)
                val status = "HTTP/1.1 $code X\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(status.toByteArray(Charsets.ISO_8859_1))
                    write(bytes)
                    flush()
                }
            }
        }
    }

    private lateinit var server: FakeServer
    private lateinit var reader: FxRateReader

    private val frankfurterLatest = """
        {"amount":1.0,"base":"EUR","date":"2026-09-25",
         "rates":{"CNY":7.6551,"JPY":179.7,"USD":1.1403}}
    """.trimIndent()

    private val ecbDailyXml = """
        <gesmes:Envelope xmlns:gesmes="x">
          <Cube><Cube time="2026-09-25">
            <Cube currency="USD" rate="1.1403"/>
            <Cube currency="CNY" rate="7.6551"/>
            <Cube currency="JPY" rate="179.7"/>
          </Cube></Cube>
        </gesmes:Envelope>
    """.trimIndent()

    private val ecbDailyNoCny = """
        <gesmes:Envelope xmlns:gesmes="x">
          <Cube><Cube time="2026-09-25">
            <Cube currency="USD" rate="1.1403"/>
            <Cube currency="JPY" rate="179.7"/>
          </Cube></Cube>
        </gesmes:Envelope>
    """.trimIndent()

    private val currencyApiLatest = """
        {"date":"2026-09-25","eur":{"cny":7.68,"jpy":180.75,"usd":1.1474,"btc":0.00002}}
    """.trimIndent()

    private val fixedToday = LocalDate.of(2026, 9, 26)

    @Before
    fun setUp() {
        server = FakeServer()
        server.respond("/fx/latest", 200, frankfurterLatest)
        server.respond("/ecb-daily", 200, ecbDailyXml)
        reader = FxRateReader(
            frankfurterBase = "${server.baseUrl}/fx/",
            ecbDailyUrl = "${server.baseUrl}/ecb-daily",
            ecbHistUrl = "${server.baseUrl}/ecb-hist",
            currencyApiBases = listOf(
                "${server.baseUrl}/cdn-{date}",
                "${server.baseUrl}/pages-{date}",
            ),
            today = { fixedToday },
        )
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun assertAllFailed(block: () -> Unit): FxException {
        try {
            block()
            fail("expected ALL_SOURCES_FAILED")
        } catch (e: FxException) {
            assertEquals(FxFailure.ALL_SOURCES_FAILED, e.failure)
            return e
        }
        error("unreachable")
    }

    @Test
    fun testLatestUsesFrankfurterFirst() {
        val rates = reader.fetchLatest()
        assertEquals("2026-09-25", rates.date)
        assertEquals(FxSource.FRANKFURTER, rates.source)
        assertEquals(7.6551, rates.eurRates.getValue("CNY"), 0.0001)
        assertTrue(server.requestedPaths.contains("/fx/latest"))
    }

    @Test
    fun testFrankfurterErrorFallsBackToEcb() {
        server.respond("/fx/latest", 500, "")
        val rates = reader.fetchLatest()
        assertEquals(FxSource.ECB, rates.source)
        assertEquals("2026-09-25", rates.date)
        assertTrue(server.requestedPaths.contains("/ecb-daily"))
    }

    @Test
    fun testEcbMissingRequiredCodeFallsBackToCurrencyApi() {
        server.respond("/fx/latest", 500, "")
        server.respond("/ecb-daily", 200, ecbDailyNoCny)
        server.respond("/cdn-latest", 200, currencyApiLatest)
        val rates = reader.fetchLatest()
        assertEquals(FxSource.CURRENCY_API, rates.source)
        assertTrue(rates.eurRates.containsKey("CNY"))
        assertFalse(rates.eurRates.containsKey("BTC"))
    }

    @Test
    fun testJsDelivrFailureFallsToPagesMirror() {
        server.respond("/fx/latest", 500, "")
        server.respond("/ecb-daily", 500, "")
        server.respond("/cdn-latest", 404, "")
        server.respond("/pages-latest", 200, currencyApiLatest)
        val rates = reader.fetchLatest()
        assertEquals(FxSource.CURRENCY_API, rates.source)
        assertTrue(server.requestedPaths.contains("/cdn-latest"))
        assertTrue(server.requestedPaths.contains("/pages-latest"))
    }

    @Test
    fun testAllSourcesFailedListsAttempts() {
        server.respond("/fx/latest", 500, "")
        server.respond("/ecb-daily", 500, "")
        val e = assertAllFailed { reader.fetchLatest() }
        val sources = e.attempts.map { it.first }
        assertTrue(FxSource.FRANKFURTER in sources)
        assertTrue(FxSource.ECB in sources)
        assertTrue(FxSource.CURRENCY_API in sources)
    }

    @Test
    fun testRangeResolvesWeekendToBusinessDay() {
        server.respond(
            "/fx/2026-09-13..2026-09-22?symbols=CNY,JPY,USD",
            200,
            """
            {"amount":1.0,"base":"EUR","start_date":"2026-09-14","end_date":"2026-09-22",
             "rates":{"2026-09-18":{"CNY":7.6,"JPY":178.0,"USD":1.14},
                      "2026-09-21":{"CNY":7.65,"JPY":179.0,"USD":1.145},
                      "2026-09-22":{"CNY":7.66,"JPY":179.7,"USD":1.146}}}
            """.trimIndent(),
        )
        val map = reader.fetchForDates(
            listOf(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22)),
            required = setOf("CNY", "JPY", "USD"),
        )
        // Sunday 09-20 resolves to Friday 09-18's rates.
        assertEquals("2026-09-18", map.getValue(LocalDate.of(2026, 9, 20)).date)
        assertEquals("2026-09-22", map.getValue(LocalDate.of(2026, 9, 22)).date)
    }

    @Test
    fun testEcbHistoryUsedWhenWithin90Days() {
        server.respond("/fx/2026-09-15..2026-09-22?symbols=CNY,JPY,USD", 500, "")
        server.respond(
            "/ecb-hist",
            200,
            """
            <gesmes:Envelope xmlns:gesmes="x"><Cube>
              <Cube time="2026-09-21">
                <Cube currency="USD" rate="1.14"/><Cube currency="CNY" rate="7.6"/>
                <Cube currency="JPY" rate="179.0"/>
              </Cube>
            </Cube></gesmes:Envelope>
            """.trimIndent(),
        )
        val map = reader.fetchForDates(
            listOf(LocalDate.of(2026, 9, 22)),
            required = setOf("CNY", "JPY", "USD"),
        )
        assertEquals(FxSource.ECB, map.getValue(LocalDate.of(2026, 9, 22)).source)
        assertEquals("2026-09-21", map.getValue(LocalDate.of(2026, 9, 22)).date)
    }

    @Test
    fun testEcbSkippedForOldDates() {
        val old = fixedToday.minusDays(100)
        server.respond("/fx/${old.minusDays(7)}..$old?symbols=CNY,JPY,USD", 500, "")
        server.respond("/cdn-$old", 200, currencyApiLatest)
        val map = reader.fetchForDates(
            listOf(old),
            required = setOf("CNY", "JPY", "USD"),
        )
        assertFalse(server.requestedPaths.any { it.contains("ecb-hist") })
        assertEquals(FxSource.CURRENCY_API, map.getValue(old).source)
    }

    @Test
    fun testCurrencyApiCapsAt12Dates() {
        server.respond("/ecb-hist", 500, "")
        // 21 requested dates; the per-call cap limits currency-api fetches to 12.
        val dates = (0..20).map { fixedToday.minusDays(it.toLong()) }
        dates.forEach { server.respond("/cdn-$it", 200, currencyApiLatest) }
        val map = reader.fetchForDates(
            dates.toSet(),
            required = setOf("CNY", "JPY", "USD"),
        )
        val caDates = server.requestedPaths
            .filter { it.startsWith("/cdn-") || it.startsWith("/pages-") }
            .size
        assertEquals(12, map.size)
        assertTrue(caDates <= 12)
    }

    @Test
    fun testPartialResolutionReturnsPartialMap() {
        server.respond("/fx/2026-09-13..2026-09-22?symbols=CNY,JPY,USD", 500, "")
        server.respond("/ecb-hist", 500, "")
        server.respond("/cdn-2026-09-22", 200, currencyApiLatest)
        val map = reader.fetchForDates(
            listOf(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22)),
            required = setOf("CNY", "JPY", "USD"),
        )
        assertEquals(setOf(LocalDate.of(2026, 9, 22)), map.keys)
    }
}
