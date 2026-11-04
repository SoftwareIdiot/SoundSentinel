package com.softwareidiot.soundsentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.ui.CctvTab
import com.softwareidiot.soundsentinel.ui.theme.BorderColor
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.DarkBackground
import com.softwareidiot.soundsentinel.ui.theme.DarkSurface
import com.softwareidiot.soundsentinel.ui.theme.DarkSurfaceVariant
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.MintActive
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.TextPrimary
import com.softwareidiot.soundsentinel.ui.theme.TextSecondary
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent

@Composable
fun SoundSentinelDrawerContent(
    selectedTab: CctvTab,
    onSelectTab: (CctvTab) -> Unit,
    isArmed: Boolean,
    recordingsCount: Int,
    usedPercentage: Float,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        drawerContainerColor = DarkBackground,
        drawerContentColor = TextPrimary,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Brand Header inside Side Drawer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(CoralPrimary, GoldAccent, VioletAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "SoundSentinel Emblem",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SoundSentinel",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isArmed) MintActive else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArmed) "Armed in Background" else "Standby Mode",
                                color = if (isArmed) MintActive else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // Navigation Items
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerNavItem(
                        title = "Home Monitor",
                        subtitle = "Live wave & background arming",
                        icon = Icons.Default.GraphicEq,
                        accentColor = CoralPrimary,
                        isSelected = selectedTab == CctvTab.SURVEILLANCE,
                        onClick = { onSelectTab(CctvTab.SURVEILLANCE) },
                        testTag = "drawer_nav_home"
                    )

                    DrawerNavItem(
                        title = "Recordings Vault",
                        subtitle = "Replay & manage audio clips",
                        icon = Icons.Default.Audiotrack,
                        accentColor = GoldAccent,
                        badgeText = if (recordingsCount > 0) "$recordingsCount" else null,
                        isSelected = selectedTab == CctvTab.VAULT,
                        onClick = { onSelectTab(CctvTab.VAULT) },
                        testTag = "drawer_nav_vault"
                    )

                    DrawerNavItem(
                        title = "Storage & Quota",
                        subtitle = "Capacity limits & retention",
                        icon = Icons.Default.Storage,
                        accentColor = CyanAccent,
                        badgeText = "${usedPercentage.toInt()}%",
                        isSelected = selectedTab == CctvTab.STORAGE,
                        onClick = { onSelectTab(CctvTab.STORAGE) },
                        testTag = "drawer_nav_storage"
                    )

                    DrawerNavItem(
                        title = "Preferences",
                        subtitle = "Auto/Manual detection & quality",
                        icon = Icons.Default.Tune,
                        accentColor = VioletAccent,
                        isSelected = selectedTab == CctvTab.PREFERENCES,
                        onClick = { onSelectTab(CctvTab.PREFERENCES) },
                        testTag = "drawer_nav_preferences"
                    )
                }
            }

            // Bottom Quick Info Card inside Drawer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Smart Acoustic Detection",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Automatically starts recording when sound is detected and continues in the background while armed.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.25f) else DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) accentColor else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
