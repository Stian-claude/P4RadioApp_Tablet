package no.p4radio.app

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class AppMode { RADIO, SPOTIFY }

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    val stations = P4_STATIONS

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _isFetchingUrls = MutableStateFlow(true)
    val isFetchingUrls: StateFlow<Boolean> = _isFetchingUrls

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _appMode = MutableStateFlow(AppMode.RADIO)
    val appMode: StateFlow<AppMode> = _appMode

    val spotifyController = SpotifyController()

    private val resolvedUrls = mutableMapOf<String, String>()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) _errorMessage.value = null
            }
            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = "Kunne ikke koble til. Sjekk nettforbindelsen."
                _isPlaying.value = false
                _isBuffering.value = false
            }
        })
    }

    init {
        viewModelScope.launch {
            fetchAllStreamUrls()
            _isFetchingUrls.value = false
            selectStation(stations.first { it.id == "p4" })
        }
    }

    private suspend fun fetchAllStreamUrls() {
        withTimeoutOrNull(9000L) {
            coroutineScope {
                stations.map { station ->
                    async {
                        RadioBrowserApi.findStreamUrl(station.searchName)?.let { url ->
                            resolvedUrls[station.id] = url
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun setMode(mode: AppMode) {
        if (_appMode.value == mode) return
        _appMode.value = mode
        when (mode) {
            AppMode.RADIO -> {
                spotifyController.disconnect()
                _currentStation.value?.let { station ->
                    val url = resolvedUrls[station.id] ?: return
                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    startService(station.name)
                }
            }
            AppMode.SPOTIFY -> {
                exoPlayer.pause()
                stopService()
                spotifyController.connect()
            }
        }
    }

    fun selectStation(station: RadioStation) {
        val url = resolvedUrls[station.id]
        if (url == null) {
            _errorMessage.value = "Stream-URL ikke tilgjengelig for ${station.name}"
            _currentStation.value = station
            return
        }
        _currentStation.value = station
        _errorMessage.value = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
        startService(station.name)
    }

    fun nextStation() {
        val idx = stations.indexOfFirst { it.id == _currentStation.value?.id }
        selectStation(stations[(idx + 1) % stations.size])
    }

    fun prevStation() {
        val idx = stations.indexOfFirst { it.id == _currentStation.value?.id }
        val prev = if (idx <= 0) stations.size - 1 else idx - 1
        selectStation(stations[prev])
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            stopService()
        } else {
            _currentStation.value?.let { station ->
                if (exoPlayer.playbackState == Player.STATE_IDLE ||
                    exoPlayer.playbackState == Player.STATE_ENDED) {
                    val url = resolvedUrls[station.id] ?: return
                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                }
                exoPlayer.play()
                startService(station.name)
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    private fun startService(stationName: String) {
        try {
            val intent = Intent(getApplication(), RadioForegroundService::class.java).apply {
                action = RadioForegroundService.ACTION_START
                putExtra(RadioForegroundService.EXTRA_STATION_NAME, stationName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (_: Exception) { }
    }

    private fun stopService() {
        try {
            getApplication<Application>().startService(
                Intent(getApplication(), RadioForegroundService::class.java).apply {
                    action = RadioForegroundService.ACTION_STOP
                }
            )
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        exoPlayer.release()
        spotifyController.disconnect()
        stopService()
        super.onCleared()
    }
}
