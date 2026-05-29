package no.p4radio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import no.p4radio.app.SpotifyController
import no.p4radio.app.SpotifyPlaylist
import no.p4radio.app.SpotifyTrack
import java.util.*

val RadioGreen = Color(0xFF5ACB7E)
val DarkGreen  = Color(0xFF0C1F12)

val RadioClockFont: FontFamily = FontFamily(Font(R.font.playfair_display))

private val DAY_NAMES   = arrayOf("Sondag","Mandag","Tirsdag","Onsdag","Torsdag","Fredag","Lordag")
private val MONTH_NAMES = arrayOf("Januar","Februar","Mars","April","Mai","Juni",
                                   "Juli","August","September","Oktober","November","Desember")

// ── Responsive size helpers (landscape) ───────────────────────────────────────
private fun clockSp(w: Int): Float      = when { w >= 600 -> 72f;  w >= 480 -> 58f;  w >= 360 -> 46f;  else -> 36f }
private fun dateSp(w: Int): Float       = when { w >= 600 -> 14f;  w >= 480 -> 13f;  w >= 360 -> 12f;  else -> 11f }
private fun stationSp(w: Int): Float    = when { w >= 600 -> 22f;  w >= 480 -> 18f;  w >= 360 -> 15f;  else -> 13f }
private fun trackTitleSp(w: Int): Float = when { w >= 600 -> 22f;  w >= 480 -> 18f;  w >= 360 -> 15f;  else -> 13f }
private fun playDp(w: Int): Int         = when { w >= 600 -> 100;  w >= 480 -> 84;   w >= 360 -> 70;   else -> 58 }
private fun skipDp(w: Int): Int         = when { w >= 600 -> 74;   w >= 480 -> 62;   w >= 360 -> 52;   else -> 42 }
private fun iconPlayDp(w: Int): Int     = when { w >= 600 -> 56;   w >= 480 -> 46;   w >= 360 -> 38;   else -> 32 }
private fun iconSkipDp(w: Int): Int     = when { w >= 600 -> 42;   w >= 480 -> 34;   w >= 360 -> 28;   else -> 24 }

// ── MainScreen ────────────────────────────────────────────────────────────────
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

    // Tablet-versjon: vis alltid landscape-UI.
    // Orienterings-APIer (config.orientation, config.screenWidthDp) er upålitelige i
    // multi-window — de reflekterer vinduets mål, ikke enhetens fysiske orientering.
    // BoxWithConstraints.maxWidth gir den eneste pålitelige vindubredden.
    // Compact-modus trigges utelukkende av vindubredde < 500 dp.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        val windowWidthDp = maxWidth.value.toInt()
        val isCompact     = windowWidthDp < 500

        // ── Content ───────────────────────────────────────────────────────────
        when (appMode) {
            AppMode.RADIO -> RadioContent(
                viewModel, clockTime, dateText,
                currentStation?.name ?: "", isPlaying, isBuffering, isFetching, errorMessage,
                screenWidthDp = windowWidthDp, isLandscape = true, isCompact = isCompact
            )
            AppMode.SPOTIFY -> SpotifyContent(
                viewModel, clockTime, dateText,
                screenWidthDp = windowWidthDp, isCompact = isCompact
            )
        }

        // ── Mode buttons ──────────────────────────────────────────────────────
        if (!isCompact) {
            // Wide (≥ 500 dp): knapper til høyre
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
            // Compact (< 500 dp): knapper nederst
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color(0xFF0A0A0A))
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactModeButton("Radio",   appMode == AppMode.RADIO)   { viewModel.setMode(AppMode.RADIO) }
                CompactModeButton("Spotify", appMode == AppMode.SPOTIFY) { viewModel.setMode(AppMode.SPOTIFY) }
            }
        }
    }
}

// ── Mode buttons ──────────────────────────────────────────────────────────────
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
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            color = if (isActive) Color(0xFF0D0D0D) else RadioGreen.copy(alpha = 0.7f))
    }
}

@Composable
fun CompactModeButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp).height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) RadioGreen else DarkGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (isActive) Color(0xFF0D0D0D) else RadioGreen.copy(alpha = 0.7f))
    }
}

// ── Radio content ─────────────────────────────────────────────────────────────
@Composable
fun RadioContent(
    viewModel: RadioViewModel,
    clockTime: String, dateText: String, currentStation: String,
    isPlaying: Boolean, isBuffering: Boolean, isFetching: Boolean,
    errorMessage: String?,
    screenWidthDp: Int,
    isLandscape: Boolean,
    isCompact: Boolean
) {
    // Portrait keeps original fixed sizes; landscape scales with width
    val clkSp   = if (isLandscape) clockSp(screenWidthDp)   else 96f
    val datFSp  = if (isLandscape) dateSp(screenWidthDp)    else 15f
    val staSp   = if (isLandscape) stationSp(screenWidthDp) else 28f
    val playSz  = if (isLandscape) playDp(screenWidthDp).dp else 104.dp
    val skipSz  = if (isLandscape) skipDp(screenWidthDp).dp else 76.dp
    val icoPlay = if (isLandscape) iconPlayDp(screenWidthDp).dp else 58.dp
    val icoSkip = if (isLandscape) iconSkipDp(screenWidthDp).dp else 42.dp

    val hPad      = if (!isLandscape) 24.dp else if (isCompact) 16.dp else 0.dp
    val startPad  = if (!isLandscape) hPad  else if (isCompact) 16.dp else 60.dp
    val endPad    = if (!isLandscape) hPad  else if (isCompact) 16.dp else 118.dp
    val bottomPad = if (isCompact) 58.dp else 12.dp

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = startPad, end = endPad, top = 12.dp, bottom = bottomPad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clockTime, fontSize = clkSp.sp, fontFamily = RadioClockFont,
                color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText, fontSize = datFSp.sp, color = RadioGreen.copy(alpha = 0.65f))
            Spacer(Modifier.weight(1f))
            PersonalMessage(screenWidthDp)
            Spacer(Modifier.weight(1f))
            RadioControls(viewModel, currentStation, isPlaying, isBuffering, isFetching,
                staSp, playSz, skipSz, icoPlay, icoSkip)
            errorMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(msg) { viewModel.clearError() }
            }
            Spacer(Modifier.height(12.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clockTime, fontSize = clkSp.sp, fontFamily = RadioClockFont,
                color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText, fontSize = datFSp.sp, color = RadioGreen.copy(alpha = 0.65f))
            Spacer(Modifier.weight(1f))
            PersonalMessage(screenWidthDp)
            Spacer(Modifier.weight(1f))
            RadioControls(viewModel, currentStation, isPlaying, isBuffering, isFetching,
                staSp, playSz, skipSz, icoPlay, icoSkip)
            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                ErrorBanner(msg) { viewModel.clearError() }
            }
            Spacer(Modifier.height(24.dp))
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

// ── Personlig hilsen ──────────────────────────────────────────────────────────
@Composable
private fun PersonalMessage(screenWidthDp: Int) {
    // 50% større enn original
    val nameSp  = when { screenWidthDp >= 600 -> 30f; screenWidthDp >= 400 -> 24f; else -> 19f }
    val heartDp = when { screenWidthDp >= 600 -> 42.dp; screenWidthDp >= 400 -> 33.dp; else -> 27.dp }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            "Stian",
            fontSize    = nameSp.sp,
            fontFamily  = RadioClockFont,
            fontWeight  = FontWeight.SemiBold,
            color       = RadioGreen
        )
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            tint     = Color(0xFFE53935),
            modifier = Modifier.size(heartDp).padding(vertical = 2.dp)
        )
        Text(
            "Nataliya",
            fontSize    = nameSp.sp,
            fontFamily  = RadioClockFont,
            fontWeight  = FontWeight.SemiBold,
            color       = RadioGreen
        )
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

// ── Spotify content ───────────────────────────────────────────────────────────
@Composable
fun SpotifyContent(
    viewModel: RadioViewModel,
    clockTime: String, dateText: String,
    screenWidthDp: Int,
    isCompact: Boolean
) {
    val spotify             = viewModel.spotifyController
    val needsAuth          by spotify.needsAuth.collectAsState()
    val connecting         by spotify.connecting.collectAsState()
    val connected          by spotify.connected.collectAsState()
    val currentTrack       by spotify.currentTrack.collectAsState()
    val isPlaying          by spotify.isPlaying.collectAsState()
    val error              by spotify.error.collectAsState()
    val tracks             by spotify.tracks.collectAsState()
    val tracksLoading      by spotify.tracksLoading.collectAsState()
    val tracksError        by spotify.tracksError.collectAsState()
    val currentPlaylistName by spotify.currentPlaylistName.collectAsState()
    val currentPlaylistId   by spotify.currentPlaylistId.collectAsState()
    val userPlaylists       by spotify.userPlaylists.collectAsState()
    val playlistsLoading    by spotify.playlistsLoading.collectAsState()

    val playSz  = playDp(screenWidthDp).dp
    val skipSz  = skipDp(screenWidthDp).dp
    val icoPlay = iconPlayDp(screenWidthDp).dp
    val icoSkip = iconSkipDp(screenWidthDp).dp
    val clkSp   = clockSp(screenWidthDp)
    val datFSp  = dateSp(screenWidthDp)
    val trkSp   = trackTitleSp(screenWidthDp)

    val showFlyoutButton = connected && !connecting && !needsAuth
    var flyoutOpen by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    LaunchedEffect(flyoutOpen) {
        if (flyoutOpen && tracks.isEmpty()) spotify.fetchPlaylistTracks()
        if (!flyoutOpen) showPlaylistPicker = false
    }

    // Content padding
    val startPad  = if (isCompact) 12.dp else 60.dp
    val endPad    = if (isCompact) 12.dp else 118.dp
    // Reserve plass til flyout-tab øverst i compact-modus (54dp knapp + 4dp gap)
    val topPad    = if (isCompact) 58.dp else 12.dp
    val bottomPad = if (isCompact) 58.dp else 12.dp

    // Bruk fillMaxSize + ingen ekstra Box-lag som kan fange touch-events
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Main column ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clockTime, fontSize = clkSp.sp, fontFamily = RadioClockFont,
                color = RadioGreen, letterSpacing = 4.sp)
            Text(dateText, fontSize = datFSp.sp, color = RadioGreen.copy(alpha = 0.55f))

            when {
                needsAuth -> {
                    Spacer(Modifier.weight(1f))
                    error?.let {
                        Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    Button(onClick = { spotify.openAuthInBrowser() },
                        colors = ButtonDefaults.buttonColors(containerColor = RadioGreen)) {
                        Text("Logg inn med Spotify", color = Color(0xFF0D0D0D),
                            fontWeight = FontWeight.SemiBold)
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
                    PersonalMessage(screenWidthDp)
                    Spacer(Modifier.weight(1f))
                    currentTrack?.let { track ->
                        Text(track.title, fontSize = trkSp.sp,
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
                            modifier = Modifier.size(skipSz)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                            Icon(Icons.Default.SkipPrevious, "Forrige", tint = Color.White,
                                modifier = Modifier.size(icoSkip))
                        }
                        IconButton(onClick = { spotify.clearError(); spotify.togglePlayPause() },
                            modifier = Modifier.size(playSz)
                                .background(RadioGreen.copy(alpha = 0.16f), CircleShape)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "Pause" else "Spill",
                                tint = RadioGreen, modifier = Modifier.size(icoPlay))
                        }
                        IconButton(onClick = { spotify.skipNext() },
                            modifier = Modifier.size(skipSz)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)) {
                            Icon(Icons.Default.SkipNext, "Neste", tint = Color.White,
                                modifier = Modifier.size(icoSkip))
                        }
                    }
                    error?.let { msg ->
                        if (msg == "NO_DEVICE") {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { spotify.clearError(); spotify.openSpotifyToPlaylist() },
                                colors = ButtonDefaults.buttonColors(containerColor = RadioGreen)
                            ) {
                                Text("Start spillelisten i Spotify", color = Color(0xFF0D0D0D),
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(msg, color = Color(0xFFFF6B6B), fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ── Flyout trigger button ─────────────────────────────────────────────
        if (showFlyoutButton) {
            if (!isCompact) {
                // Wide: left-edge tab (original behaviour)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(60.dp).height(160.dp)
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(DarkGreen)
                        .clickable { flyoutOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QueueMusic, "Spilleliste", tint = RadioGreen,
                        modifier = Modifier.size(36.dp))
                }
            } else {
                // Compact: top-centre tab — 75% av dobbel størrelse
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(54.dp).width(210.dp)
                        .clip(RoundedCornerShape(bottomStart = 21.dp, bottomEnd = 21.dp))
                        .background(DarkGreen)
                        .clickable { flyoutOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.QueueMusic, "Spilleliste", tint = RadioGreen,
                            modifier = Modifier.size(27.dp))
                        Text("Spilleliste", fontSize = 18.sp, color = RadioGreen,
                            fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ExpandMore, null,
                            tint = RadioGreen.copy(alpha = 0.7f), modifier = Modifier.size(21.dp))
                    }
                }
            }
        }

        // ── Dim overlay ───────────────────────────────────────────────────────
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

        // ── Flyout panel ──────────────────────────────────────────────────────
        if (!isCompact) {
            // Wide: slides in from the left
            AnimatedVisibility(
                visible = flyoutOpen,
                enter = slideInHorizontally(tween(280)) { -it },
                exit  = slideOutHorizontally(tween(280)) { -it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
            ) {
                Surface(color = Color(0xFF111827), shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxSize()) {
                    FlyoutContent(
                        spotify, tracks, tracksLoading, tracksError, currentTrack,
                        currentPlaylistName, currentPlaylistId, userPlaylists, playlistsLoading,
                        showPlaylistPicker,
                        onTogglePlaylistPicker = { showPlaylistPicker = it },
                        onClose = { flyoutOpen = false }
                    )
                }
            }
        } else {
            // Compact: slides down from the top
            AnimatedVisibility(
                visible = flyoutOpen,
                enter = slideInVertically(tween(280)) { -it },
                exit  = slideOutVertically(tween(280)) { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
            ) {
                Surface(color = Color(0xFF111827), shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxSize()) {
                    FlyoutContent(
                        spotify, tracks, tracksLoading, tracksError, currentTrack,
                        currentPlaylistName, currentPlaylistId, userPlaylists, playlistsLoading,
                        showPlaylistPicker,
                        onTogglePlaylistPicker = { showPlaylistPicker = it },
                        onClose = { flyoutOpen = false }
                    )
                }
            }
        }
    }
}

// ── Flyout content (shared by wide and compact) ───────────────────────────────
@Composable
private fun FlyoutContent(
    spotify: SpotifyController,
    tracks: List<SpotifyTrack>,
    tracksLoading: Boolean,
    tracksError: String?,
    currentTrack: SpotifyTrack?,
    currentPlaylistName: String,
    currentPlaylistId: String,
    userPlaylists: List<SpotifyPlaylist>,
    playlistsLoading: Boolean,
    showPlaylistPicker: Boolean,
    onTogglePlaylistPicker: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Header
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
                        onTogglePlaylistPicker(!showPlaylistPicker)
                        if (!showPlaylistPicker && userPlaylists.isEmpty()) {
                            spotify.fetchUserPlaylists()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(currentPlaylistName, fontSize = 16.sp, fontFamily = RadioClockFont,
                    color = RadioGreen, fontWeight = FontWeight.SemiBold)
                Icon(
                    if (showPlaylistPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Velg spilleliste",
                    tint = RadioGreen.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Lukk", tint = RadioGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDivider(color = RadioGreen.copy(alpha = 0.15f))

        if (showPlaylistPicker) {
            when {
                playlistsLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = RadioGreen,
                        modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
                userPlaylists.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Ingen spillelister funnet",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(userPlaylists) { playlist ->
                        val isActive = playlist.id == currentPlaylistId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    spotify.selectPlaylist(playlist)
                                    onTogglePlaylistPicker(false)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(22.dp), Alignment.Center) {
                                if (isActive) Icon(Icons.Default.PlayArrow, null,
                                    tint = RadioGreen, modifier = Modifier.size(16.dp))
                            }
                            Text(playlist.name, fontSize = 13.sp,
                                color = if (isActive) RadioGreen else Color.White.copy(alpha = 0.9f),
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (tracksLoading) {
                        CircularProgressIndicator(color = RadioGreen,
                            modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(tracksError ?: "Kunne ikke hente spilleliste",
                                color = Color(0xFFFF6B6B).copy(alpha = 0.85f),
                                fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { spotify.fetchPlaylistTracks() }) {
                                Text("Prøv igjen", color = RadioGreen, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(tracks) { index, track ->
                        val isActive = track.uri == currentTrack?.uri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { spotify.playTrack(track.uri); onClose() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(28.dp), Alignment.Center) {
                                if (isActive)
                                    Icon(Icons.Default.PlayArrow, null, tint = RadioGreen,
                                        modifier = Modifier.size(16.dp))
                                else
                                    Text("${index + 1}", fontSize = 11.sp,
                                        color = RadioGreen.copy(alpha = 0.4f))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, fontSize = 13.sp,
                                    color = if (isActive) RadioGreen else Color.White.copy(alpha = 0.9f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
                                if (track.artist.isNotEmpty())
                                    Text(track.artist, fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.45f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
