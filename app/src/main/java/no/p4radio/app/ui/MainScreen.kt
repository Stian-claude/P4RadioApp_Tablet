package no.p4radio.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.p4radio.app.RadioStation
import no.p4radio.app.RadioViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: RadioViewModel) {
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE d. MMMM", Locale("no"))
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        // Topptittel
        Text(
            text = "P4 Radio",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 20.dp, bottom = 4.dp)
        )

        // Digital klokke
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTime,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Thin,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 4.sp
                )
                Text(
                    text = currentDate,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Feil-melding
        errorMessage?.let { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = msg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK", color = Color(0xFFFF6B6B))
                    }
                }
            }
        }

        // Nå spilles card
        currentStation?.let { station ->
            NowPlayingCard(
                station = station,
                isPlaying = isPlaying,
                isLoading = isLoading,
                onToggle = { viewModel.togglePlayPause() },
                onStop = { viewModel.stop() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stasjoner
        Text(
            text = "STASJONER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.stations) { station ->
                StationCard(
                    station = station,
                    isSelected = currentStation?.id == station.id,
                    isPlaying = isPlaying && currentStation?.id == station.id,
                    onClick = { viewModel.selectStation(station) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun NowPlayingCard(
    station: RadioStation,
    isPlaying: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NÅ SPILLER",
                    fontSize = 10.sp,
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = when {
                        isLoading -> "Laster inn..."
                        isPlaying -> "• Live"
                        else -> "Pauset"
                    },
                    fontSize = 13.sp,
                    color = if (isPlaying) Color(0xFF4CAF50) else Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Spill av",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stopp",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StationCard(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1A2A3A) else Color(0xFF1A1A1A)
    val borderColor = if (isSelected) Color(0xFF00E5FF) else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color(0xFF00E5FF) else Color.White
                )
                if (station.description.isNotEmpty()) {
                    Text(
                        text = station.description,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            if (isPlaying) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        PlayingBar(delayMs = i * 150)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayingBar(delayMs: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "bar")
    val height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = delayMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barHeight"
    )
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF00E5FF))
    )
}
