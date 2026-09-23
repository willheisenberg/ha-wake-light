package de.hawakelight.probe

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fragt nur `GET /api/` ab – schaltet bewusst kein Licht, damit der Nachttest niemanden weckt. */
object HaClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun ping(baseUrl: String, token: String): String {
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                val millis = System.currentTimeMillis() - started
                if (response.isSuccessful) "OK (${millis} ms)" else "HTTP ${response.code} (${millis} ms)"
            }
        } catch (e: Exception) {
            "FEHLER ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
