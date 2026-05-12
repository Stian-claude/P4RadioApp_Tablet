package no.p4radio.app.ui

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.p4radio.app.AppMode
import no.p4radio.app.R
import no.p4radio.app.RadioViewModel
import java.util.*

val RadioGreen   = Color(0xFF5ACB7E)
val DarkGreen    = Color(0xFF1E5C38)

val RadioClockFont: FontFamily = FontFamily(Font(R.font.playfair_display))

private val DAY_NAMES   = arrayOf("Søndag","Mandag","Tirsdag","Onsdag","Torsdag","Fredag","Lørdag")
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
            val msUntilNextMinute = (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND)
            delay(msUntilNextMinute)
            updateClock()
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Modusknapper venstre side ──────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(88.dp)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ModeButton(
                        label    = "Radio",
                        isActive = appMode == AppMode.RADIO,
                        onClick  = { viewModel.setMode(AppMode.RADIO) }
                    )
                    Spacer(Modifier.height(10.dp))
                    ModeButton(
                        label    = "Spotify",
                        isActive = appMode == AppMode.SPOTIFY,
                        onClick  = { viewModel.setMode(AppMode.SPOTIFY) }
                    )
                }

                // ── Innhold ──────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (appMode) {
                        AppMode.RADIO -> RadioContent(
                            viewModel      = viewModel,
                            clockTime      = clockTime,
                            dateText       = dateText,
                            currentStation = currentStation?.name ?: "",
                            isPlaying      = isPlaying,
                            isBuffering    = isBuffering,
                            isFetching     = isFetching,
                            errorMessage   = errorMessage,
                            isLandscape    = true
                        )
                        AppMode.SPOTIFY -> SpotifyContent(
                            viewModel   = viewModel,
                            clockTime   = clockTime,
                            dateText    = dateText,
                            isLandscape = true
                        )
                    }
                }
            }
        } else {
            // ── Portrait: bare radio (klokke + kontroller) ─────────────
            RadioContent(
                viewModel      = viewModel,
                clockTime      = clockTime,
                dateText       = dateText,
                currentStation = currentStation?.name ?: "",
                isPlaying      = isPlaying,
                isBuffering    = isBuffering,
                isFetching     = isFetching,
                errorMessage   = errorMessage,
                isLandscape    = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modusknapp
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModeButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) RadioGreen else DarkGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (isActive) Color(0xFF0D0D0D) else Color.White.copy(alpha = 0.85f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Radio-innhold
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RadioContent(
    viewModel: RadioViewModel,
    clockTime: String,
    dateText: String,
    currentStation: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFetching: Boolean,
    errorMessage: String?,
    isLandscape: Boolean
) {
    val clockFontSize   = if (isLandscape) 72.sp  else 96.sp
    val dateFontSize    = if (isLandscape) 14.sp  else 15.sp
    val stationFontSize = if (isLandscape) 26.sp  else 30.sp
    val clockPadding    = if (isLandscape) 12.dp  else 28.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(clockPadding))

        Text(
            text        = clockTime,
            fontSize    = clockFontSize,
            fontFamily  = RadioClockFont,
            color       = RadioGreen,
            letterSpacing = 4.sp
        )
        Text(
            text     = dateText,
            fontSize = dateFontSize,
            color    = RadioGreen.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 0.dp, bottom = clockPadding)
        )

        if (isFetching) {
            CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(44.dp))
            Text(
                text     = "Henter stasjoner...",
                color    = RadioGreen.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = clockPadding)
            )
        } else {
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                IconButton(
                    onClick  = { viewModel.prevStation() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipPrevious, "Forrige", tint = Color.White, modifier = Modifier.size(30.dp))
                }

                IconButton(
                    onClick  = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(80.dp)
                        .background(RadioGreen.copy(alpha = 0.16f), CircleShape)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(34.dp), strokeWidth = 2.5.dp)
                    } else {
                        Icon(
                            imageVector  = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Spill",
                            tint         = RadioGreen,
                            modifier     = Modifier.size(42.dp)
                        )
                    }
                }

                IconButton(
                    onClick  = { viewModel.nextStation() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipNext, "Neste", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            Text(
                text       = currentStation,
                fontSize   = stationFontSize,
                fontFamily = RadioClockFont,
                color      = RadioGreen,
                modifier   = Modifier.padding(top = 16.dp)
            )
        }

        errorMessage?.let { msg ->
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFF3A1010), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(msg, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = Color(0xFFFF6B6B))
                }
            }
        }

        Spacer(Modifier.height(clockPadding))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spotify-innhold
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpotifyContent(
    viewModel: RadioViewModel,
    clockTime: String,
    dateText: String,
    isLandscape: Boolean
) {
    val spotify      = viewModel.spotifyController
    val connected    by spotify.connected.collectAsState()
    val connecting   by spotify.connecting.collectAsState()
    val currentTrack by spotify.currentTrack.collectAsState()
    val isPlaying    by spotify.isPlaying.collectAsState()
    val tracks       by spotify.tracks.collectAsState()
    val error        by spotify.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Klokke øverst (liten)
        Text(
            text          = clockTime,
            fontSize      = if (isLandscape) 36.sp else 48.sp,
            fontFamily    = RadioClockFont,
            color         = RadioGreen,
            letterSpacing = 3.sp
        )
        Text(
            text     = dateText,
            fontSize = 12.sp,
            color    = RadioGreen.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        when {
            connecting -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(40.dp))
                Text(
                    text     = "Kobler til Spotify...",
                    color    = RadioGreen.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(Modifier.weight(1f))
            }

            !connected -> {
                Spacer(Modifier.weight(1f))
                error?.let { msg ->
                    Text(
                        text     = msg,
                        color    = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { spotify.clearError(); spotify.connect() }) {
                        Text("Prøv igjen", color = RadioGreen)
                    }
                } ?: run {
                    Text("Ikke koblet til Spotify", color = RadioGreen.copy(0.6f), fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
            }

            else -> {
                // ── Nåværende spor ─────────────────────────────────────
                currentTrack?.let { track ->
                    Text(
                        text       = track.title,
                        fontSize   = if (isLandscape) 20.sp else 24.sp,
                        fontFamily = RadioClockFont,
                        color      = RadioGreen,
                        maxLines   = 1
                    )
                    Text(
                        text     = track.artist,
                        fontSize = 12.sp,
                        color    = RadioGreen.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Kontroller ─────────────────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier              = Modifier.padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick  = { spotify.skipPrevious() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipPrevious, "Forrige", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    IconButton(
                        onClick  = { spotify.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .background(RadioGreen.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            imageVector  = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Spill",
                            tint         = RadioGreen,
                            modifier     = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick  = { spotify.skipNext() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, "Neste", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // ── Sporsliste ─────────────────────────────────────────
                if (tracks.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Laster spor...", color = RadioGreen.copy(0.5f), fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier            = Modifier.fillMaxWidth(),
                        contentPadding      = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(tracks) { track ->
                            val isCurrent = track.uri == currentTrack?.uri
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isCurrent) RadioGreen.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { spotify.playTrack(track.uri) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text     = track.title,
                                        color    = if (isCurrent) RadioGreen else Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (track.artist.isNotBlank()) {
                                        Text(
                                            text     = track.artist,
                                            color    = Color.White.copy(alpha = 0.45f),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isCurrent && isPlaying) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint     = RadioGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Feilmelding
                error?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                }
            }
        }
    }
}
