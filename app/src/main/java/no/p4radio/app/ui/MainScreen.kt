package no.p4radio.app.ui

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import no.p4radio.app.R
import no.p4radio.app.RadioViewModel
import java.util.*

val RadioGreen = Color(0xFF5ACB7E)

val RadioClockFont: FontFamily = FontFamily(Font(R.font.playfair_display))

private val DAY_NAMES   = arrayOf("Søndag","Mandag","Tirsdag","Onsdag","Torsdag","Fredag","Lørdag")
private val MONTH_NAMES = arrayOf("Januar","Februar","Mars","April","Mai","Juni",
                                   "Juli","August","September","Oktober","November","Desember")

@Composable
fun MainScreen(viewModel: RadioViewModel) {
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

    val clockFontSize   = if (isLandscape) 72.sp  else 96.sp
    val dateFontSize    = if (isLandscape) 14.sp  else 15.sp
    val stationFontSize = if (isLandscape) 26.sp  else 30.sp
    val clockPadding    = if (isLandscape) 12.dp  else 28.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Klokke ──────────────────────────────────────────────────
            Spacer(Modifier.height(clockPadding))

            Text(
                text = clockTime,
                fontSize = clockFontSize,
                fontFamily = RadioClockFont,
                color = RadioGreen,
                letterSpacing = 4.sp
            )

            Text(
                text = dateText,
                fontSize = dateFontSize,
                color = RadioGreen.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 0.dp, bottom = clockPadding)
            )

            // ── Kontroller (ingen ramme, transparent bakgrunn) ──────────
            if (isFetching) {
                CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(44.dp))
                Text(
                    text = "Henter stasjoner...",
                    color = RadioGreen.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = clockPadding)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Forrige
                    IconButton(
                        onClick = { viewModel.prevStation() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Forrige",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Play / pause
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(80.dp)
                            .background(RadioGreen.copy(alpha = 0.16f), CircleShape)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = RadioGreen,
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Spill",
                                tint = RadioGreen,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    // Neste
                    IconButton(
                        onClick = { viewModel.nextStation() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Neste",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Stasjonsnavn i klokke-font
                Text(
                    text = currentStation?.name ?: "",
                    fontSize = stationFontSize,
                    fontFamily = RadioClockFont,
                    color = RadioGreen,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Feilmelding
            errorMessage?.let { msg ->
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF3A1010), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
}
