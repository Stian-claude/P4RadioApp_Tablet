package no.p4radio.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object RadioBrowserApi {

    private val servers = listOf(
        "de1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info",
    )

    suspend fun findStreamUrl(stationName: String, countryCode: String = "NO"): String? =
        withContext(Dispatchers.IO) {
            for (server in servers) {
                val url = try {
                    val encoded = stationName.replace(" ", "%20")
                    URL("https://$server/json/stations/search?name=$encoded&countrycode=$countryCode&order=votes&reverse=true&limit=10&hidebroken=true")
                } catch (_: Exception) { continue }

                try {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "RadioApp/1.0")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000

                    val body = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(body)

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val streamUrl = obj.optString("url_resolved").takeIf { it.isValidStream() }
                            ?: obj.optString("url").takeIf { it.isValidStream() }
                        if (streamUrl != null) return@withContext streamUrl
                    }
                } catch (_: Exception) { continue }
            }
            null
        }

    private fun String.isValidStream() =
        isNotBlank() && (startsWith("http://") || startsWith("https://"))
}
