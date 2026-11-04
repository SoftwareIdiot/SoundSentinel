package com.softwareidiot.soundsentinel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.data.model.AudioRecording
import com.softwareidiot.soundsentinel.data.model.ThreatLevel
import com.softwareidiot.soundsentinel.logic.CctvLogic
import com.softwareidiot.soundsentinel.player.PlaybackState
import com.softwareidiot.soundsentinel.ui.theme.BorderColor
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.DarkSurface
import com.softwareidiot.soundsentinel.ui.theme.DarkSurfaceVariant
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.GoldContainer
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.TextPrimary
import com.softwareidiot.soundsentinel.ui.theme.TextSecondary
import com.softwareidiot.soundsentinel.ui.theme.ThreatRed
import com.softwareidiot.soundsentinel.ui.theme.ThreatRedBg
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent
import kotlin.math.max

@Composable
fun RecordingsVaultScreen(
    recordings: List<AudioRecording>,
    playbackState: PlaybackState,
    onPlay: (AudioRecording) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Double) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleProtect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onExport: (AudioRecording) -> Unit,
    onShare: (AudioRecording) -> Unit,
    onDelete: (String) -> Unit,
    onClearUnprotected: () -> Unit,
    onReclassify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") }
    var renameTarget by remember { mutableStateOf<AudioRecording?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filteredList = recordings.filter { r ->
        val tag = r.classification?.tag?.lowercase() ?: ""
        val title = r.customTitle?.lowercase() ?: ""
        val explanation = r.classification?.explanation?.lowercase() ?: ""
        val matchesQuery = searchQuery.isBlank() ||
                r.dateString.lowercase().contains(searchQuery.lowercase()) ||
                r.id.lowercase().contains(searchQuery.lowercase()) ||
                title.contains(searchQuery.lowercase()) ||
                tag.contains(searchQuery.lowercase()) ||
                explanation.contains(searchQuery.lowercase())

        val matchesFilter = when (filterMode) {
            "loud" -> r.classification?.threatLevel == ThreatLevel.HIGH ||
                    r.classification?.threatLevel == ThreatLevel.CRITICAL ||
                    r.peakDecibels >= 70
            "protected" -> r.isProtected
            "voice" -> tag.contains("voice") || tag.contains("vocal") || tag.contains("speech")
            "faint" -> r.peakDecibels < 40 || tag.contains("faint") || tag.contains("quiet")
            else -> true
        }

        matchesQuery && matchesFilter
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = DarkSurface,
            title = {
                Text("Rename Recording", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text("Enter custom clip title", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = BorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget?.let { onRename(it.id, renameText) }
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("recordings_vault_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Clear Unprotected
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECORDINGS VAULT",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${recordings.size} clip(s) saved • ${recordings.count { it.isProtected }} locked",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (recordings.any { !it.isProtected }) {
                OutlinedButton(
                    onClick = onClearUnprotected,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThreatRed),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ThreatRed.copy(alpha = 0.4f))),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("clear_unprotected_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear unprotected",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Prune",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, sound tag, or date...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = CoralPrimary,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        )

        // Filter Mode Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf(
                "all" to "All Clips",
                "loud" to "🚨 Loud / Impacts",
                "faint" to "🔉 Faint Sounds",
                "voice" to "🗣️ Speech",
                "protected" to "🔒 Locked"
            )
            items(chips) { (key, label) ->
                val isSelected = filterMode == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = if (isSelected) {
                                Brush.horizontalGradient(listOf(CoralPrimary, VioletAccent))
                            } else {
                                Brush.horizontalGradient(listOf(DarkSurface, DarkSurface))
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) GoldAccent else BorderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { filterMode = key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Recordings List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Empty",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (recordings.isEmpty()) "No recordings saved yet" else "No matching clips found",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Arm SoundSentinel on the Home screen to start capturing sounds",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id }) { rec ->
                    RecordingCard(
                        recording = rec,
                        playbackState = playbackState,
                        onPlay = { onPlay(rec) },
                        onPause = onPause,
                        onResume = onResume,
                        onSeek = onSeek,
                        onSetSpeed = onSetSpeed,
                        onToggleProtect = { onToggleProtect(rec.id) },
                        onStartRename = {
                            renameText = rec.customTitle ?: rec.classification?.tag ?: ""
                            renameTarget = rec
                        },
                        onExport = { onExport(rec) },
                        onShare = { onShare(rec) },
                        onDelete = { onDelete(rec.id) },
                        onReclassify = { onReclassify(rec.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    recording: AudioRecording,
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Double) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleProtect: () -> Unit,
    onStartRename: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReclassify: () -> Unit
) {
    val isActive = playbackState.activeRecordingId == recording.id
    val isPlaying = isActive && playbackState.isPlaying
    val classification = recording.classification

    val progressFraction = if (isActive && recording.duration > 0) {
        (playbackState.currentPositionSeconds / recording.duration).toFloat().coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) CoralPrimary else BorderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Primary Row: Large One-Tap Play/Pause Button + Title/Badge + Lock
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        brush = if (isPlaying) {
                            Brush.linearGradient(listOf(ThreatRed, CoralPrimary))
                        } else {
                            Brush.linearGradient(listOf(CoralPrimary, VioletAccent))
                        }
                    )
                    .clickable {
                        if (isPlaying) onPause()
                        else if (isActive) onResume()
                        else onPlay()
                    }
                    .testTag("play_btn_${recording.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recording.customTitle ?: classification?.tag ?: "Audio Recording",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(CyanAccent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${recording.sampleRate / 1000.0}kHz",
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${recording.dateString} • ${CctvLogic.formatDuration(recording.duration)} • ${CctvLogic.formatBytes(recording.sizeBytes)}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = onToggleProtect,
                modifier = Modifier.size(36.dp).testTag("protect_btn_${recording.id}")
            ) {
                Icon(
                    imageVector = if (recording.isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Protect toggle",
                    tint = if (recording.isProtected) GoldAccent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Interactive Multicolor High-Resolution Sound Wave
        Column {
            InteractiveWaveformBar(
                waveformData = recording.waveformData,
                progress = progressFraction,
                isActive = isActive,
                isPlaying = isPlaying,
                onTapFraction = { frac ->
                    val targetSec = frac * recording.duration
                    if (!isActive) {
                        onPlay()
                    }
                    onSeek(targetSec)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) {
                        "${CctvLogic.formatDuration(playbackState.currentPositionSeconds)} / ${CctvLogic.formatDuration(recording.duration)}"
                    } else {
                        "Tap play or tap wave to seek • Peak ${recording.peakDecibels}/100"
                    },
                    color = if (isActive) GoldAccent else TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                            val isCurrentSpeed = (playbackState.speed - spd) in -0.05f..0.05f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isCurrentSpeed) GoldContainer else DarkSurfaceVariant)
                                    .clickable { onSetSpeed(spd) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${spd}x",
                                    color = if (isCurrentSpeed) GoldAccent else TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "${recording.triggersCount} burst(s)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (classification?.explanation != null) {
            Text(
                text = classification.explanation,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UtilityChipButton(
                    icon = Icons.Default.Edit,
                    label = "Rename",
                    onClick = onStartRename
                )
                UtilityChipButton(
                    icon = Icons.Default.FileDownload,
                    label = "Save WAV",
                    onClick = onExport
                )
                UtilityChipButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = onShare
                )
                UtilityChipButton(
                    icon = Icons.Default.GraphicEq,
                    label = "Re-Scan",
                    onClick = onReclassify
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp).testTag("delete_btn_${recording.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ThreatRed.copy(alpha = 0.75f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun UtilityChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(width = 0.5.dp, color = BorderColor, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = GoldAccent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InteractiveWaveformBar(
    waveformData: List<Int>,
    progress: Float,
    isActive: Boolean,
    isPlaying: Boolean,
    onTapFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPoints = remember(waveformData) {
        if (waveformData.isNotEmpty()) waveformData
        else List(48) { idx -> 20 + ((idx * 17) % 65) }
    }
    val playedBrush = remember {
        Brush.verticalGradient(colors = listOf(CoralPrimary, GoldAccent, CoralPrimary))
    }
    val activeUnplayedBrush = remember {
        Brush.verticalGradient(colors = listOf(CyanAccent.copy(alpha = 0.7f), VioletAccent.copy(alpha = 0.7f)))
    }
    val idleBrush = remember {
        Brush.verticalGradient(colors = listOf(CyanAccent.copy(alpha = 0.5f), VioletAccent.copy(alpha = 0.45f)))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onTapFraction(frac)
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalBars = max(1, displayPoints.size)
            val slotWidth = size.width / totalBars.toFloat()
            val barWidth = (slotWidth * 0.65f).coerceAtLeast(2f)
            val midY = size.height / 2f
            val minHalfHeightPx = 2.dp.toPx()
            val cornerRadiusPx = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())

            displayPoints.forEachIndexed { index, level ->
                val barProgress = index.toFloat() / totalBars.toFloat()
                val isPassed = isActive && barProgress <= progress
                val ratio = (level.coerceIn(10, 100) / 100f)
                val halfHeight = max(minHalfHeightPx, midY * ratio)
                val x = index * slotWidth + (slotWidth - barWidth) / 2f

                val brush = when {
                    isPassed -> playedBrush
                    isPlaying -> activeUnplayedBrush
                    else -> idleBrush
                }

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, midY - halfHeight),
                    size = Size(barWidth, halfHeight * 2f),
                    cornerRadius = cornerRadiusPx
                )
            }

            if (isActive) {
                val playheadX = (size.width * progress).coerceIn(0f, size.width)
                drawLine(
                    color = GoldAccent,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
