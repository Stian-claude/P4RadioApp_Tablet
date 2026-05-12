package no.p4radio.app

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

data class SpotifyTrack(
    val uri: String,
    val title: String,
    val artist: String
)

class SpotifyController {

    companion object {
        // 1. Gå til developer.spotify.com → Create App → kopier Client ID hit:
        const val CLIENT_ID    = "8c204e7445654c499369b5090d2977bb"
        const val REDIRECT_URI = "no.radioapp.player://callback"
        // 2. Åpne Spotify → finn spillelisten → ⋮ → Del → Kopier Spotify-URI → lim inn her:
        const val PLAYLIST_URI = "spotify:playlist:DIN_SPILLELISTE_ID_HER"
    }

    private var contextRef: WeakReference<Context>? = null
    private var remote: SpotifyAppRemote? = null

    private val _connected   = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _connecting  = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    private val _currentTrack = MutableStateFlow<SpotifyTrack?>(null)
    val currentTrack: StateFlow<SpotifyTrack?> = _currentTrack

    private val _isPlaying   = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _tracks      = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val tracks: StateFlow<List<SpotifyTrack>> = _tracks

    private val _error       = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setContext(context: Context) {
        contextRef = WeakReference(context)
    }

    fun connect() {
        if (_connected.value || _connecting.value) return
        val ctx = contextRef?.get() ?: run {
            _error.value = "Intern feil: kontekst mangler"
            return
        }
        _connecting.value = true
        _error.value = null

        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remote = appRemote
                _connected.value = true
                _connecting.value = false
                subscribeToPlayerState()
                loadPlaylistTracks()
                appRemote.playerApi.play(PLAYLIST_URI)
            }

            override fun onFailure(throwable: Throwable) {
                _connected.value = false
                _connecting.value = false
                _error.value = when {
                    throwable.message?.contains("CouldNotFindSpotifyApp", true) == true ->
                        "Spotify-appen er ikke installert"
                    throwable.message?.contains("NotLoggedIn", true) == true ->
                        "Logg inn i Spotify-appen"
                    throwable.message?.contains("UserNotAuthorized", true) == true ->
                        "Autoriser appen i Spotify-innstillinger"
                    CLIENT_ID == "DIN_CLIENT_ID_HER" ->
                        "Client ID er ikke satt — se SpotifyController.kt"
                    else -> "Kunne ikke koble til Spotify"
                }
            }
        })
    }

    fun disconnect() {
        SpotifyAppRemote.disconnect(remote)
        remote = null
        _connected.value = false
        _connecting.value = false
        _isPlaying.value = false
        _currentTrack.value = null
    }

    private fun subscribeToPlayerState() {
        remote?.playerApi?.subscribeToPlayerState()?.setEventCallback { state: PlayerState ->
            _isPlaying.value = !state.isPaused
            state.track?.let { track ->
                _currentTrack.value = SpotifyTrack(
                    uri    = track.uri,
                    title  = track.name,
                    artist = track.artist.name
                )
            }
        }
    }

    private fun loadPlaylistTracks() {
        val item = ListItem(PLAYLIST_URI, PLAYLIST_URI, null, "", "", true, true)
        remote?.contentApi
            ?.getChildrenOfItem(item, 100, 0)
            ?.setResultCallback { result ->
                _tracks.value = result.items
                    .filter { it.playable }
                    .map { li -> SpotifyTrack(uri = li.uri, title = li.title, artist = li.subtitle ?: "") }
            }
            ?.setErrorCallback {
                _error.value = "Kunne ikke laste spillelisten"
            }
    }

    fun playTrack(uri: String)  { remote?.playerApi?.play(uri) }
    fun togglePlayPause()       { remote?.let { r -> if (_isPlaying.value) r.playerApi.pause() else r.playerApi.resume() } }
    fun skipNext()              { remote?.playerApi?.skipNext() }
    fun skipPrevious()          { remote?.playerApi?.skipPrevious() }
    fun clearError()            { _error.value = null }
}
