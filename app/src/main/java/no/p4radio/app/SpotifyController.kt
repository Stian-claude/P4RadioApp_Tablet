package no.p4radio.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

data class SpotifyTrack(val uri: String, val title: String, val artist: String)

class SpotifyController(application: Application) {

    companion object {
        private const val CLIENT_ID     = "8c204e7445654c499369b5090d2977bb"
        private const val CLIENT_SECRET = "a64330afc60741e3844a4b999e88cfaf"
        private const val REDIRECT_URI  = "no.radioapp.player://callback"
        private const val PLAYLIST_ID   = "2kBChSTA8UEmfnbHycHFh9"
        private const val PLAYLIST_URI  = "spotify:playlist:2kBChSTA8UEmfnbHycHFh9"

        fun buildAuthUrl(): String = Uri.Builder()
            .scheme("https").authority("accounts.spotify.com").path("/authorize")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope",
                "user-modify-playback-state user-read-playback-state " +
                "playlist-read-private user-read-currently-playing")
            .build().toString()
    }

    private val prefs = application.getSharedPreferences("spotify", Context.MODE_PRIVATE)
    private val http  = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private var contextRef: WeakReference<Context>? = null
    private var accessToken: String? = null
    private var tokenExpiry: Long    = 0L

    private val _needsAuth    = MutableStateFlow(!hasToken())
    val needsAuth: StateFlow<Boolean> = _needsAuth
    private val _connecting   = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting
    private val _connected    = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _tracks       = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val tracks: StateFlow<List<SpotifyTrack>> = _tracks
    private val _currentTrack = MutableStateFlow<SpotifyTrack?>(null)
    val currentTrack: StateFlow<SpotifyTrack?> = _currentTrack
    private val _isPlaying    = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _error        = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _statusMsg    = MutableStateFlow("")
    val statusMsg: StateFlow<String> = _statusMsg

    fun setContext(ctx: Context) { contextRef = WeakReference(ctx) }
    private fun hasToken() = prefs.getString("refresh_token", null) != null

    fun openAuthInBrowser() {
        val ctx = contextRef?.get() ?: return
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildAuthUrl()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun handleAuthCode(code: String) = withContext(Dispatchers.IO) {
        _connecting.value = true
        _statusMsg.value  = "Henter tilgangstoken..."
        try {
            val body = FormBody.Builder()
                .add("grant_type",    "authorization_code")
                .add("code",          code)
                .add("redirect_uri",  REDIRECT_URI)
                .add("client_id",     CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            val json = doPost("https://accounts.spotify.com/api/token", body)
            val obj  = JSONObject(json)
            prefs.edit().putString("refresh_token", obj.getString("refresh_token")).apply()
            accessToken = obj.getString("access_token")
            tokenExpiry = System.currentTimeMillis() + (obj.getInt("expires_in") - 60) * 1000L
            _needsAuth.value = false
            loadAndPlay()
        } catch (e: Exception) {
            _connecting.value = false
            _error.value = "Autorisering feilet: ${e.message}"
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (!hasToken()) { _needsAuth.value = true; return@withContext }
        if (_connected.value || _connecting.value) return@withContext
        _connecting.value = true
        _statusMsg.value  = "Kobler til Spotify..."
        try {
            ensureToken()
            loadAndPlay()
        } catch (e: Exception) {
            _connecting.value = false
            _error.value = "Feil: ${e.message}"
        }
    }

    private fun loadAndPlay() {
        _statusMsg.value = "Laster spilleliste..."
        val token = accessToken!!
        val resp  = doGet(
            "https://api.spotify.com/v1/playlists/$PLAYLIST_ID/tracks" +
            "?limit=100&fields=items(track(name,uri,artists(name)))", token)
        val items = JSONObject(resp).getJSONArray("items")
        val list  = mutableListOf<SpotifyTrack>()
        for (i in 0 until items.length()) {
            val track  = items.getJSONObject(i).optJSONObject("track") ?: continue
            val uri    = track.optString("uri").takeIf { it.isNotBlank() } ?: continue
            val name   = track.optString("name")
            val artist = track.optJSONArray("artists")
                ?.optJSONObject(0)?.optString("name") ?: ""
            list += SpotifyTrack(uri, name, artist)
        }
        _tracks.value     = list
        playContext(PLAYLIST_URI)
        _connected.value  = true
        _connecting.value = false
        _statusMsg.value  = ""
    }

    suspend fun pollPlaybackState() {
        if (!_connected.value) return
        try {
            ensureToken()
            val resp = try { doGet("https://api.spotify.com/v1/me/player", accessToken!!) }
                       catch (_: Exception) { return }
            if (resp.isBlank()) return
            val obj = JSONObject(resp)
            _isPlaying.value = obj.optBoolean("is_playing", false)
            val item = obj.optJSONObject("item") ?: return
            _currentTrack.value = SpotifyTrack(
                uri    = item.optString("uri"),
                title  = item.optString("name"),
                artist = item.optJSONArray("artists")
                    ?.optJSONObject(0)?.optString("name") ?: ""
            )
        } catch (_: Exception) { }
    }

    suspend fun playTrack(uri: String) = withContext(Dispatchers.IO) {
        try { ensureToken(); playContext(PLAYLIST_URI, uri) } catch (_: Exception) { }
    }

    suspend fun togglePlayPause() = withContext(Dispatchers.IO) {
        try {
            ensureToken()
            if (_isPlaying.value)
                doPut("https://api.spotify.com/v1/me/player/pause", null, accessToken!!)
            else
                doPut("https://api.spotify.com/v1/me/player/play", null, accessToken!!)
        } catch (_: Exception) { }
    }

    suspend fun skipNext() = withContext(Dispatchers.IO) {
        try { ensureToken(); doPostEmpty("https://api.spotify.com/v1/me/player/next", accessToken!!) }
        catch (_: Exception) { }
    }

    suspend fun skipPrevious() = withContext(Dispatchers.IO) {
        try { ensureToken(); doPostEmpty("https://api.spotify.com/v1/me/player/previous", accessToken!!) }
        catch (_: Exception) { }
    }

    fun disconnect() {
        _connected.value    = false
        _connecting.value   = false
        _isPlaying.value    = false
        _currentTrack.value = null
    }

    fun clearError() { _error.value = null }

    private suspend fun ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) return
        val refresh = prefs.getString("refresh_token", null)
            ?: throw Exception("Ingen refresh token")
        val body = FormBody.Builder()
            .add("grant_type",    "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id",     CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        val obj = JSONObject(doPost("https://accounts.spotify.com/api/token", body))
        accessToken = obj.getString("access_token")
        tokenExpiry = System.currentTimeMillis() + (obj.getInt("expires_in") - 60) * 1000L
        obj.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
            prefs.edit().putString("refresh_token", it).apply()
        }
    }

    private fun playContext(contextUri: String, trackUri: String? = null) {
        val body = buildString {
            append("{\"context_uri\":\"$contextUri\"")
            if (trackUri != null) append(",\"offset\":{\"uri\":\"$trackUri\"}")
            append("}")
        }
        doPut("https://api.spotify.com/v1/me/player/play", body, accessToken!!)
    }

    private fun doGet(url: String, token: String): String {
        val resp = http.newCall(Request.Builder().url(url)
            .header("Authorization", "Bearer $token").build()).execute()
        if (resp.code == 204) return ""
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        return resp.body?.string() ?: ""
    }

    private fun doPost(url: String, body: okhttp3.RequestBody): String {
        val resp = http.newCall(Request.Builder().url(url).post(body).build()).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()}")
        return resp.body?.string() ?: ""
    }

    private fun doPostEmpty(url: String, token: String) {
        http.newCall(Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody()).build()).execute()
    }

    private fun doPut(url: String, jsonBody: String?, token: String) {
        val body = (jsonBody ?: "").toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .put(body).build()).execute()
    }
}
