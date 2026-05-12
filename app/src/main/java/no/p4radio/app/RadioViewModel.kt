package no.p4radio.app

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    val stations = P4_STATIONS

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                _isLoading.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) _errorMessage.value = null
            }
            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = "Kan ikke koble til stasjonen"
                _isPlaying.value = false
                _isLoading.value = false
            }
        })
    }

    fun selectStation(station: RadioStation) {
        _currentStation.value = station
        _errorMessage.value = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(station.streamUrl))
        exoPlayer.prepare()
        exoPlayer.play()
        startService(station.name)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            stopService()
        } else {
            _currentStation.value?.let { station ->
                if (exoPlayer.playbackState == Player.STATE_IDLE ||
                    exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(station.streamUrl))
                    exoPlayer.prepare()
                }
                exoPlayer.play()
                startService(station.name)
            }
        }
    }

    fun stop() {
        exoPlayer.stop()
        stopService()
    }

    fun clearError() {
        _errorMessage.value = null
    }

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
        } catch (_: Exception) { /* tjenesten starter ikke, avspilling fortsetter */ }
    }

    private fun stopService() {
        try {
            val intent = Intent(getApplication(), RadioForegroundService::class.java).apply {
                action = RadioForegroundService.ACTION_STOP
            }
            getApplication<Application>().startService(intent)
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        exoPlayer.release()
        stopService()
        super.onCleared()
    }
}
