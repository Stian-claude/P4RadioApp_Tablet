package no.p4radio.app.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.p4radio.app.AppMode
import no.p4radio.app.R
import no.p4radio.app.RadioViewModel
import java.util.*

val RadioGreen = Color(0xFF5ACB7E)
val DarkGreen  = Color(0xFF0C1F12)

val RadioClockFont: FontFamily = FontFamily(Font(R.font.playfair_display))

private val DAY_NAMES   = arrayOf("Sondag","Mandag","Tirsdag","Onsdag","Torsdag","Fredag","Lordag")
private val MONTH_NAMES = arrayOf("Januar","Februar","Mars","April","Mai","Juni",
                                   "Juli","August","September","Oktober","November","Desember")

@Composable
fun MainScreen(viewModel: RadioViewModel) {
    val appMode        by viewModel.appMode.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying      by viewModel.isPlaying.collectAsState()
    val isBuffering    by viewModel.isBuffering.collectAsState()
    val isFetching     by viewModel.isFetchingUrls.collectAsState()
    val errorMessage   by viewModel.errorMessage.collectAsState()

    var clockTime by remember { mutableStateOf("") }
    var dateText  by remember { mutableStateOf("") }

    fun updateClock() {
        val cal   = Calendar.getInstance()
        val h     = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m     = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        clockTime = "$h:$m"
        val day   = DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val date  = cal.get(Calendar.DAY_OF_MONTH)
        val month = MONTH_NAMES[cal.get(Calendar.MONTH)]
        val year  = cal.get(Calendar.YEAR)
        dateText  = "$day $date. $month $year"
    }

    LaunchedEffect(Unit) {
        updateClock()
        while (true) {
            val cal = Calendar.getInstance()
            val ms  = (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND)
            delay(ms)
            updateClock()
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var prevLandscape by rememberSaveable { mutableStateOf(isLandscape) }
    LaunchedEffect(isLandscape) {
        if (!isLandscape && prevLandscape && viewModel.appMode.value == AppMode.SPOTIFY) {
            viewModel.spotifyController.pauseIfPlaying()
        }
        prevLandscape = isLandscape
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        if (isLandscape) {
            when (appMode) {
                AppMode.RADIO   -> RadioContent(viewModel, clockTime, dateText,
                    currentStation?.name ?: "", isPlaying, isBuffering, isFetching, errorMessage,
                    isLandscape = true)
                AppMode.SPOTIFY -> SpotifyContent(viewModel, clockTime, dateText, isLandscape = true)
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(118.dp)
                    .padding(end = 14.dp)
                    .align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ModeButton("Radio",   appMode == AppMode.RADIO)   { viewModel.setMode(AppMode.RADIO) }
                Spacer(Modifier.height(12.dp))
                ModeButton("Spotify", appMode == AppMode.SPOTIFY) { viewModel.setMode(AppMode.SPOTIFY) }
            }
        } else {
            RadioContent(viewModel, clockTime, dateText,
                currentStation?.name ?: "", isPlaying, isBuffering, isFetching, errorMessage,
                isLandscape = false)
        }
    }
}

@Composable
fun ModeButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(102.dp).height(57.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) RadioGreen else DarkGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (isActive) Color(0xFF0D0D0D) else RadioGreen.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun RadioContent(
    viewModel: RadioViewModel,
    clockTime: String, dateText: String, currentStation: String,
    isPlaying: Boolean, isBuffering: Boolean, isFetching: Boolean,
    errorMessage: String?, isLandscape: Boolean
) {
    val clockSize   : Dp
    val dateSize    : Float
    val stationSize : Float
    val playSize    : Dp
    val skipSize    : Dp
    val iconPlay    : Dp
    val iconSkip    : Dp

    if (isLandscape) {
        clockSize   = 72.dp; dateSize = 14f; stationSize = 22f
        playSize = 100.dp; skipSize = 74.dp; iconPlay = 56.dp; iconSkip = 42.dp
    } else {
        clockSize   = 96.dp; dateSize = 15f; stationSize = 28f
        playSize = 104.dp; skipSize = 76.dp; iconPlay = 58.dp; iconSkip = 42.dp
    }

    if (isLandscape) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 60.dp, end = 118.dp, top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clockTime, fontSize = clockSize.value.sp, fontFamily = RadioClockFont,
                color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText, fontSize = dateSize.sp, color = RadioGreen.copy(alpha = 0.65f))

            Spacer(Modifier.weight(1f))

            RadioControls(viewModel, currentStation, isPlaying, isBuffering, isFetching,
                stationSize, playSize, skipSize, iconPlay, iconSkip)

            errorMessage?.let { msg -> ErrorBanner(msg) { viewModel.clearError() } }
            Spacer(Modifier.height(20.dp))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(clockTime, fontSize = clockSize.value.sp, fontFamily = RadioClockFont,
                color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText, fontSize = dateSize.sp, color = RadioGreen.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 32.dp))

            RadioControls(viewModel, currentStation, isPlaying, isBuffering, isFetching,
                stationSize, playSize, skipSize, iconPlay, iconSkip)

            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                ErrorBanner(msg) { viewModel.clearError() }
            }
        }
    }
}

@Composable
private fun RadioControls(
    viewModel: RadioViewModel,
    currentStation: String,
    isPlaying: Boolean, isBuffering: Boolean, isFetching: Boolean,
    stationSize: Float, playSize: Dp, skipSize: Dp, iconPlay: Dp, iconSkip: Dp
) {
    if (isFetching) {
        CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(44.dp))
        Text("Henter stasjoner...", color = RadioGreen.copy(alpha = 0.6f), fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp))
    } else {
        Text(currentStation, fontSize = stationSize.sp, fontFamily = RadioClockFont,
            color = RadioGreen, modifier = Modifier.padding(bottom = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = { viewModel.prevStation() },
                modifier = Modifier.size(skipSize).background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                Icon(Icons.Default.SkipPrevious, "Forrige", tint = Color.White,
                    modifier = Modifier.size(iconSkip))
            }
            IconButton(onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(playSize).background(RadioGreen.copy(alpha = 0.16f), CircleShape)) {
                if (isBuffering)
                    CircularProgressIndicator(color = RadioGreen,
                        modifier = Modifier.size(playSize * 0.44f), strokeWidth = 3.dp)
                else
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Spill",
                        tint = RadioGreen, modifier = Modifier.size(iconPlay))
            }
            IconButton(onClick = { viewModel.nextStation() },
                modifier = Modifier.size(skipSize).background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                Icon(Icons.Default.SkipNext, "Neste", tint = Color.White,
                    modifier = Modifier.size(iconSkip))
            }
        }
    }
}

@Composable
private fun ErrorBanner(msg: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .background(Color(0xFF3A1010), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(msg, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("OK", color = Color(0xFFFF6B6B)) }
    }
}

@Composable
fun SpotifyContent(
    viewModel: RadioViewModel,
    clockTime: String, dateText: String, isLandscape: Boolean
) {
    val spotify        = viewModel.spotifyController
    val needsAuth    by spotify.needsAuth.collectAsState()
    val connecting   by spotify.connecting.collectAsState()
    val connected    by spotify.connected.collectAsState()
    val currentTrack by spotify.currentTrack.collectAsState()
    val isPlaying    by spotify.isPlaying.collectAsState()
    val error        by spotify.error.collectAsState()
    val tracks       by spotify.tracks.collectAsState()
    val tracksLoading by spotify.tracksLoading.collectAsState()
    val tracksError   by spotify.tracksError.collectAsState()
    val currentPlaylistName by spotify.currentPlaylistName.collectAsState()
    val currentPlaylistId   by spotify.currentPlaylistId.collectAsState()
    val userPlaylists       by spotify.userPlaylists.collectAsState()
    val playlistsLoading    by spotify.playlistsLoading.collectAsState()

    val playSize = if (isLandscape) 100.dp else 104.dp
    val skipSize = if (isLandscape) 74.dp  else 76.dp
    val iconPlay = if (isLandscape) 56.dp  else 58.dp
    val iconSkip = 42.dp

    val showFlyoutButton = isLandscape && connected && !connecting && !needsAuth
    var flyoutOpen by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    LaunchedEffect(flyoutOpen) {
        if (flyoutOpen && tracks.isEmpty()) {
            spotify.fetchPlaylistTracks()
        }
        if (!flyoutOpen) showPlaylistPicker = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start  = if (isLandscape) 60.dp else 20.dp,
                    end    = if (isLandscape) 118.dp else 20.dp,
                    top    = 12.dp,
                    bottom = 12.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clockTime,
                fontSize = if (isLandscape) 72.sp else 96.sp,
                fontFamily = RadioClockFont, color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText,
                fontSize = if (isLandscape) 14.sp else 15.sp,
                color = RadioGreen.copy(alpha = 0.55f))

            when {
                needsAuth -> {
                    Spacer(Modifier.weight(1f))
                    error?.let {
                        Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    Button(onClick = { spotify.openAuthInBrowser() },
                        colors = ButtonDefaults.buttonColors(containerColor = RadioGreen)) {
                        Text("Logg inn med Spotify", color = Color(0xFF0D0D0D), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                }

                connecting -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(44.dp))
                    Text("Kobler til Spotify...", color = RadioGreen.copy(alpha = 0.7f),
                        fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.weight(1f))
                }

                !connected -> {
                    Spacer(Modifier.weight(1f))
                    error?.let { msg ->
                        Text(msg, color = Color(0xFFFF6B6B), fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { spotify.clearError(); spotify.connect() }) {
                            Text("Prøv igjen", color = RadioGreen)
                        }
                    } ?: Text("Ikke koblet til Spotify", color = RadioGreen.copy(0.6f), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                }

                else -> {
                    Spacer(Modifier.weight(1f))

                    currentTrack?.let { track ->
                        Text(track.title,
                            fontSize = if (isLandscape) 22.sp else 26.sp,
                            fontFamily = RadioClockFont, color = RadioGreen,
                            maxLines = 1, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Text(track.artist, fontSize = 13.sp,
                            color = RadioGreen.copy(alpha = 0.65f),
                            modifier = Modifier.padding(bottom = 14.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        IconButton(onClick = { spotify.skipPrevious() },
                            modifier = Modifier.size(skipSize).background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                            Icon(Icons.Default.SkipPrevious, "Forrige", tint = Color.White,
                                modifier = Modifier.size(iconSkip))
                        }
                        IconButton(onClick = { spotify.clearError(); spotify.togglePlayPause() },
                            modifier = Modifier.size(playSize).background(RadioGreen.copy(alpha = 0.16f), CircleShape)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "Pause" else "Spill",
                                tint = RadioGreen, modifier = Modifier.size(iconPlay))
                        }
                        IconButton(onClick = { spotify.skipNext() },
                            modifier = Modifier.size(skipSize).background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                            Icon(Icons.Default.SkipNext, "Neste", tint = Color.White,
                                modifier = Modifier.size(iconSkip))
                        }
                    }

                    error?.let { msg ->
                        if (msg == "NO_DEVICE") {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { spotify.clearError(); spotify.openSpotifyToPlaylist() },
                                colors = ButtonDefaults.buttonColors(containerColor = RadioGreen)) {
                                Text("Start spillelisten i Spotify", color = Color(0xFF0D0D0D),
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(msg, color = Color(0xFFFF6B6B), fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // Flyout tab button — left edge, only when connected landscape
        if (showFlyoutButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(60.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(DarkGreen)
                    .clickable { flyoutOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = "Spilleliste",
                    tint = RadioGreen,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Dim overlay
        AnimatedVisibility(
            visible = flyoutOpen,
            enter = fadeIn(tween(200)),
            exit  = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { flyoutOpen = false }
            )
        }

        // Flyout panel
        AnimatedVisibility(
            visible = flyoutOpen,
            enter = slideInHorizontally(tween(280)) { -it },
            exit  = slideOutHorizontally(tween(280)) { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
        ) {
            Surface(
                color = Color(0xFF111827),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with clickable playlist name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    showPlaylistPicker = !showPlaylistPicker
                                    if (showPlaylistPicker && userPlaylists.isEmpty()) {
                                        spotify.fetchUserPlaylists()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                currentPlaylistName,
                                fontSize = 16.sp,
                                fontFamily = RadioClockFont,
                                color = RadioGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (showPlaylistPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Velg spilleliste",
                                tint = RadioGreen.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { flyoutOpen = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Lukk",
                                tint = RadioGreen.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = RadioGreen.copy(alpha = 0.15f))

                    if (showPlaylistPicker) {
                        // Playlist picker
                        if (playlistsLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = RadioGreen,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (userPlaylists.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Ingen spillelister funnet",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(userPlaylists) { playlist ->
                                    val isActive = playlist.id == currentPlaylistId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                spotify.selectPlaylist(playlist)
                                                showPlaylistPicker = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.width(22.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isActive) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = RadioGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            playlist.name,
                                            fontSize = 13.sp,
                                            color = if (isActive) RadioGreen else Color.White.copy(alpha = 0.9f),
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Track list
                        if (tracks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tracksLoading) {
                                    CircularProgressIndicator(
                                        color = RadioGreen,
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            tracksError ?: "Kunne ikke hente spilleliste",
                                            color = Color(0xFFFF6B6B).copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        TextButton(onClick = { spotify.fetchPlaylistTracks() }) {
                                            Text("Prøv igjen", color = RadioGreen, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                itemsIndexed(tracks) { index, track ->
                                    val isActive = track.uri == currentTrack?.uri
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                spotify.playTrack(track.uri)
                                                flyoutOpen = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.width(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isActive) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = RadioGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    "${index + 1}",
                                                    fontSize = 11.sp,
                                                    color = RadioGreen.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                track.title,
                                                fontSize = 13.sp,
                                                color = if (isActive) RadioGreen else Color.White.copy(alpha = 0.9f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (track.artist.isNotEmpty()) {
                                                Text(
                                                    track.artist,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.45f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
