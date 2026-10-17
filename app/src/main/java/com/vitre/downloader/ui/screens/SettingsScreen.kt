package com.vitre.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*

@Composable
fun SettingsScreen(
    currentQuality: String,
    onQualityChange: (String) -> Unit,
    currentAudioFormat: String,
    onAudioFormatChange: (String) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    enableAgsl: Boolean,
    onEnableAgslChange: (Boolean) -> Unit,
    hasCookies: Boolean,
    onNavigateCookies: () -> Unit,
    onNavigateDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(OledBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Quality Section
            item {
                Text(text = "Download Preferences", style = MaterialTheme.typography.titleSmall, color = ElectricBlue)
                Spacer(modifier = Modifier.height(10.dp))

                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Preferred Video Quality", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("best", "1080p", "720p", "480p").forEach { q ->
                                FilterChip(
                                    selected = currentQuality == q,
                                    onClick = { onQualityChange(q) },
                                    label = { Text(q.uppercase()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue,
                                        selectedLabelColor = OledBlack,
                                        containerColor = CardSurface,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = "Preferred Audio Format", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("mp3", "m4a", "opus").forEach { af ->
                                FilterChip(
                                    selected = currentAudioFormat == af,
                                    onClick = { onAudioFormatChange(af) },
                                    label = { Text(af.uppercase()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue,
                                        selectedLabelColor = OledBlack,
                                        containerColor = CardSurface,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Appearance & Glass Section
            item {
                Text(text = "Appearance & Liquid Glass", style = MaterialTheme.typography.titleSmall, color = ElectricBlue)
                Spacer(modifier = Modifier.height(10.dp))

                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "AGSL GPU Shaders (Android 13+)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(
                                    text = if (enableAgsl) "Native AGSL shaders active" else "Material 3 fallback active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = enableAgsl,
                                onCheckedChange = onEnableAgslChange,
                                colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Glass Refraction Intensity: ${(glassIntensity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Slider(
                            value = glassIntensity,
                            onValueChange = onGlassIntensityChange,
                            valueRange = 0.2f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue,
                                inactiveTrackColor = CardSurface
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Authentication & Privacy
            item {
                Text(text = "Privacy & Diagnostics", style = MaterialTheme.typography.titleSmall, color = ElectricBlue)
                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavCard(
                    icon = Icons.Default.Key,
                    title = "Authentication & Cookies",
                    subtitle = if (hasCookies) "Cookies configured locally" else "No cookies imported",
                    onClick = onNavigateCookies
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavCard(
                    icon = Icons.Default.Analytics,
                    title = "Engine Diagnostics",
                    subtitle = "Inspect yt-dlp, FFmpeg, and native runtimes",
                    onClick = onNavigateDiagnostics
                )
            }
        }
    }
}

@Composable
fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        cornerRadius = 16.dp,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}
