package no.p4radio.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.ListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Base64

data class SpotifyTrack(val uri: String, val title: String, val artist: String)
data class SpotifyPlaylist(val id: String, val uri: String, val name: String)

class SpotifyController {

    companion object {
        const val CLIENT_ID     = "8c204e7445654c499369b5090d2977bb"
        const val CLIENT_SECRET = "a64330afc60741e3844a4b999e88cfaf"
        const val REDIRECT_URI  = "no.radioapp.player://callback"
        const val PLAYLIST_ID   = "2kBChSTA8UEmfnbHycHFh9"
        const val PLAYLIST_URI  = "spotify:playlist:$PLAYLIST_ID"
        const val ERROR_NO_DEVICE = "NO_DEVICE"
        private const val PREFS       = "spotify"
        private const val KEY_REFRESH = "refresh_token"
        private const val SCOPES      =
            "playlist-read-private playlist-read-collaborative " +
            "user-read-playback-state user-modify-playback-state user-read-email"
    }

    private var contextRef: WeakReference<Context>? = null
    private val http  = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var appRemote: SpotifyAppRemote? = null

    private var lastKnownTrackUri: String? = null

    private var accessToken: String? = null
    private var tokenExpiry: Long    = 0L
    private var pollingJob: Job?     = null

    private val _needsAuth  = MutableStateFlow(false)
    val needsAuth: StateFlow<Boolean> = _needsAuth

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    private val _connected  = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _currentTrack = MutableStateFlow<SpotifyTrack?>(null)
    val currentTrack: StateFlow<SpotifyTrack?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _tracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val tracks: StateFlow<List<SpotifyTrack>> = _tracks

    private val _tracksLoading = MutableStateFlow(false)
    val tracksLoading: StateFlow<Boolean> = _tracksLoading

    private val _tracksError = MutableStateFlow<String?>(null)
    val tracksError: StateFlow<String?> = _tracksError

    // Active playlist — defaults to the hardcoded "Road trip" playlist
    private val _currentPlaylistId   = MutableStateFlow(PLAYLIST_ID)
    private val _currentPlaylistUri  = MutableStateFlow(PLAYLIST_URI)
    private val _currentPlaylistName = MutableStateFlow("Road trip")
    val currentPlaylistId:   StateFlow<String> = _currentPlaylistId
    val currentPlaylistName: StateFlow<String> = _currentPlaylistName

    private val _userPlaylists    = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<SpotifyPlaylist>> = _userPlaylists

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading: StateFlow<Boolean> = _playlistsLoading

    fun setContext(context: Context) {
        contextRef = WeakReference(context)
    }

    fun onAppResumed() {
        if (!_connected.value) return
        if (appRemote?.isConnected != true) {
            reconnectAppRemote()
        }
    }

    fun connect() {
        if (_connecting.value || _connected.value) return
        _connecting.value = true
        _error.value = null

        val ctx = contextRef?.get() ?: run {
            _connecting.value = false
            _error.value = "Intern feil: kontekst mangler"
            return
        }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedRefresh = prefs.getString(KEY_REFRESH, null)
        if (savedRefresh == null) {
            _connecting.value = false
            _needsAuth.value  = true
            return
        }
        connectAppRemote(ctx)
        scope.launch {
            try { refreshUserToken(savedRefresh, ctx) } catch (_: Exception) { }
        }
    }

    private fun connectAppRemote(ctx: Context) {
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()

        var settled = false

        fun settle(connected: Boolean, remote: SpotifyAppRemote?) {
            if (settled) return
            settled = true
            if (connected && remote != null) {
                Log.d("SpotifyCtrl", "App Remote connected")
                appRemote = remote
                _connected.value  = true
                _connecting.value = false
                _needsAuth.value  = false
                _error.value      = null
                // Alltid start med spilleliste-kontekst — aldri enkeltspor-URI.
                // Enkeltspor-kontekst via remote.playerApi.play(trackUri) ødelegger skip.
                scope.launch { playPlaylistFrom(lastKnownTrackUri) }
                remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
                    _isPlaying.value = !state.isPaused
                    state.track?.let { t ->
                        lastKnownTrackUri = t.uri
                        _currentTrack.value = SpotifyTrack(t.uri, t.name, t.artist.name)
                    }
                }
                scope.launch {
                    delay(600)
                    fetchPlaylistTracks()
                }
            } else {
                Log.w("SpotifyCtrl", "App Remote not available — falling back to Web API")
                appRemote = null
                scope.launch { connectViaWebApi() }
            }
        }

        SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                settle(true, remote)
                remote.playerApi.getPlayerState().setResultCallback { state ->
                    _isPlaying.value = !state.isPaused
                    state.track?.let { t ->
                        _currentTrack.value = SpotifyTrack(t.uri, t.name, t.artist.name)
                    }
                }
            }
            override fun onFailure(throwable: Throwable) {
                Log.w("SpotifyCtrl", "App Remote onFailure: $throwable")
                settle(false, null)
            }
        })

        scope.launch {
            delay(4000)
            if (!settled) {
                Log.w("SpotifyCtrl", "App Remote timeout — falling back to Web API")
                kotlinx.coroutines.withContext(Dispatchers.Main) { settle(false, null) }
            }
        }
    }

    private fun reconnectAppRemote() {
        val ctx = contextRef?.get() ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_REFRESH, null) == null) return
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()
        var settled = false
        SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                settled = true
                appRemote = remote
                remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
                    _isPlaying.value = !state.isPaused
                    state.track?.let { t ->
                        lastKnownTrackUri = t.uri
                        _currentTrack.value = SpotifyTrack(t.uri, t.name, t.artist.name)
                    }
                }
                if (_tracks.value.isEmpty()) {
                    scope.launch { delay(300); fetchPlaylistTracks() }
                }
            }
            override fun onFailure(throwable: Throwable) {
                settled = true
            }
        })
        scope.launch { delay(4000); if (!settled) Log.w("SpotifyCtrl", "reconnect timeout") }
    }

    private suspend fun connectViaWebApi() {
        val ctx = contextRef?.get() ?: run { _connecting.value = false; _needsAuth.value = true; return }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REFRESH, null)
        if (saved != null) {
            try {
                refreshUserToken(saved, ctx)
                _connected.value  = true
                _connecting.value = false
                _needsAuth.value  = false
                startPolling()
                ensureSpotifyActive()
                fetchPlaylistTracks()
            } catch (_: Exception) {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_REFRESH).apply()
                _connecting.value = false
                _needsAuth.value  = true
            }
        } else {
            _connecting.value = false
            _needsAuth.value  = true
        }
    }

    fun fetchPlaylistTracks() {
        if (_tracksLoading.value) return
        _tracksLoading.value = true
        _tracksError.value = null
        val remote = appRemote
        if (remote?.isConnected == true) {
            val playlistUri  = _currentPlaylistUri.value
            val playlistName = _currentPlaylistName.value
            val item = ListItem(playlistUri, playlistUri, null, playlistName, "", false, true)
            remote.contentApi.getChildrenOfItem(item, 50, 0)
                .setResultCallback { result ->
                    val list = result.items.filter { it.uri.isNotEmpty() }
                        .map { SpotifyTrack(it.uri, it.title, it.subtitle ?: "") }
                    if (list.isNotEmpty()) {
                        _tracks.value = list
                        _tracksError.value = null
                        _tracksLoading.value = false
                    } else {
                        scope.launch { fetchTracksViaWebApi(); _tracksLoading.value = false }
                    }
                }
                .setErrorCallback { _ ->
                    scope.launch { fetchTracksViaWebApi(); _tracksLoading.value = false }
                }
        } else {
            scope.launch { fetchTracksViaWebApi(); _tracksLoading.value = false }
        }
    }

    // Spill spilleliste med valgfri offset-sang via Web API.
    // Bruker context_uri så skip neste/forrige alltid fungerer innen spillelisten.
    private suspend fun playPlaylistFrom(trackUri: String?) {
        try {
            ensureUserToken()
            val token    = accessToken ?: run {
                appRemote?.playerApi?.play(_currentPlaylistUri.value)
                return
            }
            val deviceId = findDeviceId(token)
            val baseUrl  = "https://api.spotify.com/v1/me/player/play"
            val url      = if (deviceId != null) "$baseUrl?device_id=$deviceId" else baseUrl
            val bodyStr  = if (trackUri != null)
                """{"context_uri":"${_currentPlaylistUri.value}","offset":{"uri":"$trackUri"}}"""
            else
                """{"context_uri":"${_currentPlaylistUri.value}"}"""
            val resp = http.newCall(
                Request.Builder()
                    .url(url)
                    .put(bodyStr.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $token")
                    .build()
            ).execute()
            if (!resp.isSuccessful) {
                // Fallback: spill spillelisten fra starten
                appRemote?.playerApi?.play(_currentPlaylistUri.value)
            }
        } catch (e: Exception) {
            Log.w("SpotifyCtrl", "playPlaylistFrom failed: $e")
            appRemote?.playerApi?.play(_currentPlaylistUri.value)
        }
    }

    private suspend fun fetchTracksViaWebApi() {
        try {
            ensureUserToken()
            val token = accessToken ?: run { _tracksError.value = "Token mangler – logg inn på nytt"; return }
            val targetPlaylistId = _currentPlaylistId.value
            var tracksHref: String? = null
            var offset = 0
            outer@ while (offset < 200) {
                val meResp = http.newCall(
                    Request.Builder()
                        .url("https://api.spotify.com/v1/me/playlists?limit=50&offset=$offset")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                if (!meResp.isSuccessful) break
                val meJson = JSONObject(meResp.body?.string() ?: "")
                val plItems = meJson.optJSONArray("items") ?: break
                for (i in 0 until plItems.length()) {
                    val pl = plItems.getJSONObject(i)
                    if (pl.optString("id") == targetPlaylistId) {
                        tracksHref = pl.optJSONObject("items")?.optString("href")?.takeIf { it.isNotEmpty() }
                            ?: pl.optJSONObject("tracks")?.optString("href")?.takeIf { it.isNotEmpty() }
                        break@outer
                    }
                }
                val next = meJson.optString("next").takeIf { it.isNotEmpty() && it != "null" } ?: break
                offset += 50
            }
            val url = (tracksHref ?: "https://api.spotify.com/v1/playlists/$targetPlaylistId/tracks")
                .let { if ("limit=" !in it) "$it?limit=100" else it }
            val tracksResp = http.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            ).execute()
            val tracksBody = tracksResp.body?.string() ?: ""
            if (!tracksResp.isSuccessful) {
                if (tracksResp.code == 403) {
                    _tracksError.value = "Denne spillelisten er ikke tilgjengelig"
                } else {
                    val msg = try {
                        JSONObject(tracksBody).optJSONObject("error")?.optString("message") ?: tracksBody.take(80)
                    } catch (_: Exception) { tracksBody.take(80) }
                    _tracksError.value = if (tracksHref != null) "href HTTP ${tracksResp.code}: $msg"
                                         else "HTTP ${tracksResp.code}: $msg"
                }
                Log.w("SpotifyCtrl", "tracks fetch: ${tracksResp.code} — $tracksBody")
                return
            }
            val respJson = JSONObject(tracksBody)
            val trackItems = respJson.optJSONArray("items") ?: run {
                val keys = respJson.keys().asSequence().toList().joinToString(",")
                _tracksError.value = "Ingen items-felt. Nøkler: [$keys]"
                return
            }
            val list = parseTrackItems(trackItems)
            if (list.isNotEmpty()) {
                _tracks.value = list
                _tracksError.value = null
            } else {
                _tracksError.value = "Ingen sanger funnet"
            }
        } catch (e: Exception) {
            _tracksError.value = "Feil: ${e.message}"
        }
    }

    private fun parseTrackItems(items: org.json.JSONArray): List<SpotifyTrack> {
        val list = mutableListOf<SpotifyTrack>()
        for (i in 0 until items.length()) {
            val obj   = items.getJSONObject(i)
            val track = obj.optJSONObject("item") ?: obj.optJSONObject("track") ?: continue
            val uri   = track.optString("uri").takeIf { it.isNotEmpty() } ?: continue
            val name  = track.optString("name")
            val arr   = track.optJSONArray("artists")
            val artist = if (arr != null)
                (0 until arr.length()).joinToString(", ") { arr.getJSONObject(it).getString("name") }
            else ""
            list.add(SpotifyTrack(uri, name, artist))
        }
        return list
    }

    fun fetchUserPlaylists() {
        if (_playlistsLoading.value) return
        _playlistsLoading.value = true
        scope.launch {
            try {
                ensureUserToken()
                val token = accessToken ?: run { _playlistsLoading.value = false; return@launch }
                val playlists = mutableListOf<SpotifyPlaylist>()
                var offset = 0
                while (offset < 500) {
                    val resp = http.newCall(
                        Request.Builder()
                            .url("https://api.spotify.com/v1/me/playlists?limit=50&offset=$offset")
                            .header("Authorization", "Bearer $token")
                            .build()
                    ).execute()
                    if (!resp.isSuccessful) break
                    val json = JSONObject(resp.body?.string() ?: "")
                    val items = json.optJSONArray("items") ?: break
                    for (i in 0 until items.length()) {
                        val pl = items.getJSONObject(i)
                        val id   = pl.optString("id").takeIf { it.isNotEmpty() } ?: continue
                        val name = pl.optString("name").takeIf { it.isNotEmpty() } ?: continue
                        val uri  = pl.optString("uri").takeIf { it.isNotEmpty() } ?: "spotify:playlist:$id"
                        playlists.add(SpotifyPlaylist(id, uri, name))
                    }
                    val next = json.optString("next").takeIf { it.isNotEmpty() && it != "null" } ?: break
                    offset += 50
                }
                _userPlaylists.value = playlists
            } catch (e: Exception) {
                Log.w("SpotifyCtrl", "fetchUserPlaylists: $e")
            } finally {
                _playlistsLoading.value = false
            }
        }
    }

    fun selectPlaylist(playlist: SpotifyPlaylist) {
        _currentPlaylistId.value   = playlist.id
        _currentPlaylistUri.value  = playlist.uri
        _currentPlaylistName.value = playlist.name
        _tracks.value = emptyList()
        lastKnownTrackUri = null
        val remote = appRemote
        if (remote?.isConnected == true) {
            remote.playerApi.play(playlist.uri)
        } else {
            scope.launch {
                try {
                    ensureUserToken()
                    val token    = accessToken ?: return@launch
                    val deviceId = findDeviceId(token)
                    if (deviceId != null) startPlaylistOnDevice(token, deviceId)
                    else _error.value = ERROR_NO_DEVICE
                } catch (e: Exception) { _error.value = "Feil: ${e.message}" }
            }
        }
        scope.launch { delay(600); fetchPlaylistTracks() }
    }

    fun openAuthInBrowser() {
        val ctx = contextRef?.get() ?: return
        val url = "https://accounts.spotify.com/authorize" +
            "?client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&scope=${Uri.encode(SCOPES)}" +
            "&show_dialog=true"
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun handleAuthCode(code: String) {
        scope.launch {
            val ctx = contextRef?.get() ?: return@launch
            _connecting.value = true
            _needsAuth.value  = false
            _error.value      = null
            try {
                val creds = Base64.getEncoder()
                    .encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
                val body = ("grant_type=authorization_code&code=$code" +
                    "&redirect_uri=${Uri.encode(REDIRECT_URI)}")
                    .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val resp = http.newCall(
                    Request.Builder()
                        .url("https://accounts.spotify.com/api/token")
                        .post(body)
                        .header("Authorization", "Basic $creds")
                        .build()
                ).execute()
                val json = JSONObject(resp.body!!.string())
                if (!resp.isSuccessful)
                    throw Exception(json.optString("error_description", "HTTP ${resp.code}"))
                accessToken = json.getString("access_token")
                tokenExpiry = System.currentTimeMillis() + json.getLong("expires_in") * 1000
                val refresh = json.getString("refresh_token")
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_REFRESH, refresh).apply()
                kotlinx.coroutines.withContext(Dispatchers.Main) { connectAppRemote(ctx) }
            } catch (e: Exception) {
                _connecting.value = false
                _needsAuth.value  = true
                _error.value      = "Innlogging feilet: ${e.message}"
            }
        }
    }

    private fun ensureSpotifyActive() {
        scope.launch {
            try {
                ensureUserToken()
                val token = accessToken ?: return@launch
                val deviceId = findDeviceId(token)
                if (deviceId != null) {
                    val uri = lastKnownTrackUri
                    if (uri != null) {
                        val resp = http.newCall(
                            Request.Builder()
                                .url("https://api.spotify.com/v1/me/player/play?device_id=$deviceId")
                                .put("""{"uris":["$uri"]}""".toRequestBody("application/json".toMediaType()))
                                .header("Authorization", "Bearer $token")
                                .build()
                        ).execute()
                        if (!resp.isSuccessful) startPlaylistOnDevice(token, deviceId)
                    } else {
                        startPlaylistOnDevice(token, deviceId)
                    }
                } else {
                    _error.value = ERROR_NO_DEVICE
                }
            } catch (e: Exception) { Log.w("SpotifyCtrl", "ensureSpotifyActive: $e") }
        }
    }

    fun openSpotifyToPlaylist() {
        val ctx = contextRef?.get() ?: return
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_currentPlaylistUri.value)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?.let { ctx.startActivity(it) }
        }
    }

    private fun refreshUserToken(refreshToken: String, ctx: Context) {
        val creds = Base64.getEncoder()
            .encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
        val body = "grant_type=refresh_token&refresh_token=$refreshToken"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val resp = http.newCall(
            Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .header("Authorization", "Basic $creds")
                .build()
        ).execute()
        val json = JSONObject(resp.body!!.string())
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        accessToken = json.getString("access_token")
        tokenExpiry = System.currentTimeMillis() + json.getLong("expires_in") * 1000
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { newR ->
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_REFRESH, newR).apply()
        }
    }

    private fun ensureUserToken() {
        val ctx = contextRef?.get() ?: return
        if (System.currentTimeMillis() > tokenExpiry - 60_000) {
            val r = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REFRESH, null) ?: throw Exception("Ingen refresh token")
            refreshUserToken(r, ctx)
        }
    }

    private fun findDeviceId(token: String): String? {
        val resp = http.newCall(
            Request.Builder()
                .url("https://api.spotify.com/v1/me/player/devices")
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        if (!resp.isSuccessful) return null
        val devices = JSONObject(resp.body!!.string()).optJSONArray("devices") ?: return null
        var fallback: String? = null
        for (i in 0 until devices.length()) {
            val d  = devices.getJSONObject(i)
            val id = d.optString("id").takeIf { it.isNotEmpty() } ?: continue
            if (d.optBoolean("is_active")) return id
            if (fallback == null) fallback = id
        }
        return fallback
    }

    private fun startPlaylistOnDevice(token: String, deviceId: String) {
        val resp = http.newCall(
            Request.Builder()
                .url("https://api.spotify.com/v1/me/player/play?device_id=$deviceId")
                .put("""{"context_uri":"${_currentPlaylistUri.value}"}""".toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        if (!resp.isSuccessful)
            _error.value = friendlyPlaybackError(resp.code, resp.body?.string())
    }

    private fun pollOnce() {
        try {
            ensureUserToken()
            val token = accessToken ?: return
            val resp = http.newCall(
                Request.Builder()
                    .url("https://api.spotify.com/v1/me/player")
                    .header("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val bodyStr = resp.body?.string() ?: return
            if (resp.code == 204 || bodyStr.isBlank()) return
            val json = JSONObject(bodyStr)
            _isPlaying.value = json.optBoolean("is_playing", false)
            val item = json.optJSONObject("item") ?: return
            val uri    = item.optString("uri")
            val name   = item.optString("name")
            val arr    = item.optJSONArray("artists")
            val artist = if (arr != null)
                (0 until arr.length()).joinToString(", ") { arr.getJSONObject(it).getString("name") }
            else ""
            lastKnownTrackUri = uri
            _currentTrack.value = SpotifyTrack(uri, name, artist)
        } catch (_: Exception) { }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) { pollOnce(); delay(3000) }
        }
    }

    fun togglePlayPause() {
        val remote = appRemote
        if (remote?.isConnected == true) {
            if (_isPlaying.value) remote.playerApi.pause() else remote.playerApi.resume()
            return
        }
        scope.launch {
            try {
                ensureUserToken()
                val token = accessToken ?: return@launch
                if (_isPlaying.value) {
                    http.newCall(Request.Builder()
                        .url("https://api.spotify.com/v1/me/player/pause")
                        .put("".toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "Bearer $token")
                        .build()).execute()
                } else {
                    val deviceId = findDeviceId(token)
                    if (deviceId == null) {
                        _error.value = ERROR_NO_DEVICE
                        return@launch
                    }
                    if (_currentTrack.value != null) {
                        val resp = http.newCall(Request.Builder()
                            .url("https://api.spotify.com/v1/me/player/play?device_id=$deviceId")
                            .put("{}".toRequestBody("application/json".toMediaType()))
                            .header("Authorization", "Bearer $token")
                            .build()).execute()
                        if (!resp.isSuccessful) startPlaylistOnDevice(token, deviceId)
                    } else {
                        startPlaylistOnDevice(token, deviceId)
                    }
                }
            } catch (e: Exception) { _error.value = "Feil: ${e.message}" }
        }
    }

    fun skipNext() {
        val remote = appRemote
        if (remote?.isConnected == true) {
            remote.playerApi.skipNext()
                .setErrorCallback { scope.launch { skipViaWebApi(next = true) } }
            return
        }
        scope.launch { skipViaWebApi(next = true) }
    }

    fun skipPrevious() {
        val remote = appRemote
        if (remote?.isConnected == true) {
            remote.playerApi.skipPrevious()
                .setErrorCallback { scope.launch { skipViaWebApi(next = false) } }
            return
        }
        scope.launch { skipViaWebApi(next = false) }
    }

    // Web API skip med retry: etter modusbytte er enheten ikke alltid "aktiv" ennå,
    // da gir POST /next 404 — vi prøver på nytt med eksplisitt device_id.
    private suspend fun skipViaWebApi(next: Boolean) {
        val endpoint = if (next) "next" else "previous"
        try {
            ensureUserToken()
            val token = accessToken ?: return
            val url = "https://api.spotify.com/v1/me/player/$endpoint"
            val resp = http.newCall(
                Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $token")
                    .build()
            ).execute()
            if (resp.code == 404) {
                // Ingen aktiv enhet registrert ennå — prøv med eksplisitt device_id
                val deviceId = findDeviceId(token) ?: return
                http.newCall(
                    Request.Builder()
                        .url("$url?device_id=$deviceId")
                        .post("".toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "Bearer $token")
                        .build()
                ).execute()
            }
        } catch (e: Exception) { Log.w("SpotifyCtrl", "skip $endpoint failed: $e") }
    }

    fun playTrack(uri: String) {
        // Bruk Web API med spilleliste-kontekst + offset slik at skip neste/forrige
        // fungerer etter valg av sang. App Remote play(uri) setter enkelt-spor-kontekst
        // som gjør at skip stopper avspillingen.
        scope.launch {
            try {
                ensureUserToken()
                val token    = accessToken ?: return@launch
                val deviceId = findDeviceId(token)
                val baseUrl  = "https://api.spotify.com/v1/me/player/play"
                val url      = if (deviceId != null) "$baseUrl?device_id=$deviceId" else baseUrl
                val body     = """{"context_uri":"${_currentPlaylistUri.value}","offset":{"uri":"$uri"}}"""
                    .toRequestBody("application/json".toMediaType())
                val resp = http.newCall(
                    Request.Builder().url(url).put(body)
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                if (!resp.isSuccessful) {
                    // Fallback: spill enkeltspor hvis spilleliste-kontekst feiler
                    val fallbackBody = """{"uris":["$uri"]}"""
                        .toRequestBody("application/json".toMediaType())
                    http.newCall(
                        Request.Builder().url(url).put(fallbackBody)
                            .header("Authorization", "Bearer $token").build()
                    ).execute()
                }
            } catch (e: Exception) { _error.value = "Feil: ${e.message}" }
        }
    }

    fun resumePlayback() {
        // Tving alltid fersk App Remote-tilkobling ved retur til Spotify.
        // Unngår alle problemer med ustabil/sitter-fast tilstand etter modusbytte.
        // settle() sørger for spilleliste-kontekst → skip fungerer garantert.
        appRemote?.let { old ->
            try { SpotifyAppRemote.disconnect(old) } catch (_: Exception) { }
        }
        appRemote = null
        val ctx = contextRef?.get() ?: run { _needsAuth.value = true; return }
        // Kaller connectAppRemote direkte — settle() setter _connected=true og starter
        // musik med playPlaylistFrom(). Ingen UI-flimmer fordi _connected forblir true.
        connectAppRemote(ctx)
    }

    fun pauseIfPlaying() {
        if (!_isPlaying.value) return
        val remote = appRemote
        if (remote?.isConnected == true) { remote.playerApi.pause(); return }
        scope.launch {
            try {
                ensureUserToken()
                val token = accessToken ?: return@launch
                http.newCall(Request.Builder()
                    .url("https://api.spotify.com/v1/me/player/pause")
                    .put("".toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $token")
                    .build()).execute()
            } catch (_: Exception) { }
        }
    }

    private fun friendlyPlaybackError(code: Int, body: String?): String {
        if (body != null) try {
            val reason = JSONObject(body).optJSONObject("error")?.optString("reason")
            if (reason == "NO_ACTIVE_DEVICE") return ERROR_NO_DEVICE
        } catch (_: Exception) { }
        return "Avspillingsfeil $code"
    }

    fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _connected.value    = false
        _connecting.value   = false
        _isPlaying.value    = false
        _currentTrack.value = null
        _needsAuth.value    = false
        _error.value        = null
        _tracks.value        = emptyList()
        _tracksLoading.value = false
        _tracksError.value   = null
        _userPlaylists.value    = emptyList()
        _playlistsLoading.value = false
        _currentPlaylistId.value   = PLAYLIST_ID
        _currentPlaylistUri.value  = PLAYLIST_URI
        _currentPlaylistName.value = "Road trip"
    }

    fun clearError() { _error.value = null }
}
