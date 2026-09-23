package de.willheisenberg.hawakelight.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import de.willheisenberg.hawakelight.color.Rgb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Fehler mit einer Meldung, die in der Oberfläche angezeigt werden kann. */
class HaException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Eine Entität aus Home Assistant, reduziert auf das, was die App braucht. */
data class HaEntity(
    val entityId: String,
    val name: String,
    val state: String,
    /** Wärmster Weißton, den die Lampe kann – kleinere Kelvin-Werte sind wärmer. */
    val minColorTempKelvin: Int? = null,
    /** Kältester Weißton, den die Lampe kann. */
    val maxColorTempKelvin: Int? = null,
)

/** Zugriff auf die REST-API von Home Assistant. Alle Aufrufe liefern ein [Result]. */
class HaClient(
    baseUrl: String,
    private val token: String,
    private val http: OkHttpClient = defaultClient,
) {
    private val base = baseUrl.trim().trimEnd('/')

    suspend fun ping(): Result<Unit> = request(Request.Builder().url("$base/api/")).map { }

    suspend fun lights(): Result<List<HaEntity>> =
        request(Request.Builder().url("$base/api/states")).mapCatching { body ->
            json.parseToJsonElement(body).let { it as JsonArray }
                .map { it.jsonObject }
                .filter { it.string("entity_id")?.startsWith("light.") == true }
                .map { entity ->
                    val id = entity.string("entity_id")!!
                    val attributes = entity["attributes"]?.jsonObject
                    HaEntity(
                        entityId = id,
                        name = attributes?.string("friendly_name") ?: id,
                        state = entity.string("state") ?: "unknown",
                        minColorTempKelvin = attributes?.int("min_color_temp_kelvin"),
                        maxColorTempKelvin = attributes?.int("max_color_temp_kelvin"),
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

    /** Prüft, ob es die Entität gibt, und liefert ihren Zustand. */
    suspend fun state(entityId: String): Result<HaEntity> =
        request(
            builder = Request.Builder().url("$base/api/states/$entityId"),
            notFoundMessage = "Entität $entityId gibt es in Home Assistant nicht",
        ).mapCatching { body ->
            val entity = json.parseToJsonElement(body).jsonObject
            val attributes = entity["attributes"]?.jsonObject
            HaEntity(
                entityId = entityId,
                name = attributes?.string("friendly_name") ?: entityId,
                state = entity.string("state") ?: "unknown",
                minColorTempKelvin = attributes?.int("min_color_temp_kelvin"),
                maxColorTempKelvin = attributes?.int("max_color_temp_kelvin"),
            )
        }

    /**
     * Schaltet das Licht ein. [transitionSeconds] = 0 bedeutet ohne Übergang,
     * [rgbColor] hat Vorrang vor [colorTempKelvin]; sind beide null, bleibt die Farbe unverändert.
     */
    suspend fun turnOn(
        entityId: String,
        brightnessPct: Int,
        transitionSeconds: Int,
        colorTempKelvin: Int? = null,
        rgbColor: Rgb? = null,
    ): Result<Unit> {
        val payload = buildJsonObject {
            put("entity_id", entityId)
            put("brightness_pct", brightnessPct.coerceIn(1, 100))
            if (transitionSeconds > 0) put("transition", transitionSeconds)
            // Farbe und Farbtemperatur schließen sich in Home Assistant gegenseitig aus.
            if (rgbColor != null) {
                put("rgb_color", JsonArray(rgbColor.asList().map { JsonPrimitive(it) }))
            } else if (colorTempKelvin != null) {
                put("color_temp_kelvin", colorTempKelvin)
            }
        }
        return request(
            Request.Builder()
                .url("$base/api/services/light/turn_on")
                .post(payload.toString().toRequestBody(jsonMediaType)),
        ).map { }
    }

    /** Schaltet das Licht aus, optional mit Übergang. */
    suspend fun turnOff(entityId: String, transitionSeconds: Int = 0): Result<Unit> {
        val payload = buildJsonObject {
            put("entity_id", entityId)
            if (transitionSeconds > 0) put("transition", transitionSeconds)
        }
        return request(
            Request.Builder()
                .url("$base/api/services/light/turn_off")
                .post(payload.toString().toRequestBody(jsonMediaType)),
        ).map { }
    }

    private suspend fun request(
        builder: Request.Builder,
        notFoundMessage: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = builder.header("Authorization", "Bearer $token").build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string().orEmpty())
                } else {
                    Result.failure(HaException(describe(response, notFoundMessage)))
                }
            }
        } catch (e: IllegalArgumentException) {
            Result.failure(HaException("Adresse ist ungültig: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(HaException("Home Assistant nicht erreichbar: ${e.message}", e))
        }
    }

    private fun describe(response: Response, notFoundMessage: String?): String = when (response.code) {
        401, 403 -> "Token wurde abgelehnt (HTTP ${response.code})"
        404 -> notFoundMessage ?: "Adresse nicht gefunden (HTTP 404)"
        else -> "Home Assistant antwortet mit HTTP ${response.code}"
    }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        private val jsonMediaType = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
