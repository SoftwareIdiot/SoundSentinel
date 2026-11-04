package com.softwareidiot.soundsentinel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.ui.theme.BorderColor
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.DarkBackground
import com.softwareidiot.soundsentinel.ui.theme.DarkSurface
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.MintActive
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.TextPrimary
import com.softwareidiot.soundsentinel.ui.theme.ThreatRed
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent
import java.util.Locale

@Composable
fun CctvTopBar(
    currentSectionTitle: String,
    isArmed: Boolean,
    isRecording: Boolean,
    recordingDuration: Double,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("cctv_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // 3-Line Hamburger Menu Button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(
                        width = 1.dp,
                        color = BorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("drawer_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SoundSentinel",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Subtle multicolor accent dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(CoralPrimary, GoldAccent, CyanAccent)
                                )
                            )
                    )
                }
                Text(
                    text = currentSectionTitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right Status Pill (REC indicator or Armed/Standby pill)
        if (isRecording) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ThreatRed.copy(alpha = 0.18f))
                    .border(width = 1.dp, color = ThreatRed.copy(alpha = 0.65f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = "Recording active",
                    tint = ThreatRed,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = String.format(Locale.US, "REC %.1fs", recordingDuration),
                    color = ThreatRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isArmed) MintActive.copy(alpha = 0.14f) else DarkSurface)
                    .border(
                        width = 1.dp,
                        color = if (isArmed) MintActive.copy(alpha = 0.5f) else BorderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isArmed) MintActive else TextMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isArmed) "ARMED • BG" else "STANDBY",
                    color = if (isArmed) MintActive else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
