package no.p4radio.app.ui

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.p4radio.app.RadioViewModel
import java.util.*

// Skoda-inspirert grønn
val RadioGreen = Color(0xFF5ACB7E)

@Composable
fun MainScreen(viewModel: RadioViewModel) {
    val currentStation  by viewModel.currentStation.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val isBuffering     by viewModel.isBuffering.collectAsState()
    val isFetchingUrls  by viewModel.isFetchingUrls.collectAsState()
    val errorMessage    by viewModel.errorMessage.collectAsState()

    var clockTime by remember { mutableStateOf("") }
    var dateText  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val months = arrayOf(
            "Januar","Februar","Mars","April","Mai","Juni",
            "Juli","August","September","Oktober","November","Desember"
        )
        while (true) {
            val cal = Calendar.getInstance()
            val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2,'0')
            val m = cal.get(Calendar.MINUTE).toString().padStart(2,'0')
            clockTime = "$h:$m"
            val day   = cal.get(Calendar.DAY_OF_MONTH)
            val month = months[cal.get(Calendar.MONTH)]
            val year  = cal.get(Calendar.YEAR)
            dateText = "$day. $month $year"
            delay(10_000) // oppdater hvert 10. sek (minutter endres sjelden)
        }
    }

    // Trigges umiddelbart ved oppstart
    LaunchedEffect(Unit) {
        val cal = Calendar.getInstance()
        val months = arrayOf(
            "Januar","Februar","Mars","April","Mai","Juni",
            "Juli","August","September","Oktober","November","Desember"
        )
        val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2,'0')
        val m = cal.get(Calendar.MINUTE).toString().padStart(2,'0')
        clockTime = "$h:$m"
        val day   = cal.get(Calendar.DAY_OF_MONTH)
        val month = months[cal.get(Calendar.MONTH)]
        val year  = cal.get(Calendar.YEAR)
        dateText = "$day. $month $year"
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val clockBlock: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            Text(
                text = clockTime,
                fontSize = if (isLandscape) 80.sp else 88.sp,
                fontWeight = FontWeight.Thin,
                fontFamily = FontFamily.Monospace,
                color = RadioGreen,
                letterSpacing = 6.sp
            )
            Text(
                text = dateText,
                fontSize = 15.sp,
                color = RadioGreen.copy(alpha = 0.65f),
                fontFamily = FontFamily.Default,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    val controlsBlock: @Composable () -> Unit = {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 1f else 0.92f)
                .padding(vertical = if (isLandscape) 0.dp else 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFetchingUrls) {
                    CircularProgressIndicator(color = RadioGreen, modifier = Modifier.size(40.dp))
                    Text(
                        text = "Henter stasjoner...",
                        color = RadioGreen.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Forrige stasjon
                        IconButton(
                            onClick = { viewModel.prevStation() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Forrige stasjon",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play / pause (stor)
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(RadioGreen.copy(alpha = 0.18f), CircleShape)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = RadioGreen,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Spill",
                                    tint = RadioGreen,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Neste stasjon
                        IconButton(
                            onClick = { viewModel.nextStation() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Neste stasjon",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stasjonsnavn
                    Text(
                        text = currentStation?.name ?: "",
                        fontSize = 14.sp,
                        color = RadioGreen,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Feilmelding
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 1f else 0.92f)
                    .background(Color(0xFF3A1010), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = msg, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = Color(0xFFFF6B6B))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .systemBarsPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    clockBlock()
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        controlsBlock()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                clockBlock()
                Spacer(modifier = Modifier.height(16.dp))
                controlsBlock()
            }
        }
    }
}
