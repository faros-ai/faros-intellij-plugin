package ai.faros.intellij.services

import ai.faros.intellij.model.CodingEvent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.Logger

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object FarosApiClient {
    private val LOG = Logger.getInstance(FarosApiClient::class.java)
    private val GSON: Gson = GsonBuilder().create()

    /**
     * Sends events to the Faros API
     *
     * @param events The list of events to send
     * @param category The category of events
     * @return true if successful, false otherwise
     */
    @JvmStatic
    fun sendEvents(events: List<CodingEvent>, category: String): Boolean {
        if (events.isEmpty()) {
            return true
        }

        val settings = FarosSettingsService.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isNullOrEmpty()) {
            LOG.warn("No API key configured for Faros API")
            return false
        }

        try {
            val url = URL(settings.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.doOutput = true

            val payload = mapOf(
                "graph" to settings.graph,
                "origin" to settings.origin,
                "category" to category,
                "events" to events
            )

            val jsonPayload = GSON.toJson(payload)

            conn.outputStream.use { os ->
                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                return true
            } else {
                LOG.warn("Failed to send events to Faros API: HTTP $responseCode")
                return false
            }
        } catch (e: IOException) {
            LOG.error("Error sending events to Faros API", e)
            return false
        }
    }
} 