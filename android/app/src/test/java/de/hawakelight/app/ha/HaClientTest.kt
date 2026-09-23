package de.hawakelight.app.ha

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HaClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = HaClient(server.url("/").toString(), "geheim")

    @Test
    fun `ping schickt Token und meldet Erfolg`() = runTest {
        server.enqueue(MockResponse().setBody("""{"message":"API running."}"""))

        val result = client().ping()

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/", request.path)
        assertEquals("Bearer geheim", request.getHeader("Authorization"))
    }

    @Test
    fun `falscher Token meldet verstaendlichen Fehler`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = client().ping().exceptionOrNull()

        assertTrue(error is HaException)
        assertTrue(error!!.message!!.contains("Token"))
    }

    @Test
    fun `lights liefert nur Lampen mit Anzeigenamen`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"entity_id":"light.danszimmer","state":"off","attributes":{"friendly_name":"Dans Zimmer"}},
                  {"entity_id":"switch.kaffee","state":"on","attributes":{"friendly_name":"Kaffee"}},
                  {"entity_id":"light.flur","state":"on","attributes":{}}
                ]
                """.trimIndent(),
            ),
        )

        val lights = client().lights().getOrThrow()

        assertEquals(listOf("light.danszimmer", "light.flur"), lights.map { it.entityId })
        assertEquals("Dans Zimmer", lights[0].name)
        assertEquals("light.flur", lights[1].name)
    }

    @Test
    fun `unbekannte Entitaet wird beim Pruefen erkannt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val error = client().state("light.gibtsnicht").exceptionOrNull()

        assertTrue(error is HaException)
        assertTrue(error!!.message!!.contains("light.gibtsnicht"))
    }

    @Test
    fun `turnOn schickt Helligkeit und Uebergangsdauer`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        client().turnOn("light.danszimmer", brightnessPct = 100, transitionSeconds = 1200).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/services/light/turn_on", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""entity_id":"light.danszimmer""""))
        assertTrue(body.contains(""""brightness_pct":100"""))
        assertTrue(body.contains(""""transition":1200"""))
    }

    @Test
    fun `turnOn ohne Uebergang laesst transition weg`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        client().turnOn("light.danszimmer", brightnessPct = 40, transitionSeconds = 0).getOrThrow()

        assertTrue(!server.takeRequest().body.readUtf8().contains("transition"))
    }

    @Test
    fun `turnOff ruft den passenden Dienst auf`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        client().turnOff("light.danszimmer", transitionSeconds = 2).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/services/light/turn_off", request.path)
        assertTrue(request.body.readUtf8().contains(""""transition":2"""))
    }

    @Test
    fun `Aufruf wird nach einem Netzfehler wiederholt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("[]"))

        val result = withRetry(attempts = 3, delayMillis = { 0L }) {
            client().turnOn("light.danszimmer", brightnessPct = 100, transitionSeconds = 0)
        }

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Wiederholung gibt nach den Versuchen auf`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = withRetry(attempts = 3, delayMillis = { 0L }) {
            client().ping()
        }

        assertTrue(result.isFailure)
        assertEquals(3, server.requestCount)
    }
}
