package com.vitre.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitre.downloader.ui.theme.*

@Composable
fun DeveloperSupportDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    isLoadingAd: Boolean = false,
    statusMessage: String? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CardSurface,
                            DarkCharcoal
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ElectricBlue.copy(alpha = 0.5f),
                            CardBorder
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Heart Glowing Icon Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricBlue.copy(alpha = 0.25f),
                                    Color(0xFFFF3377).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF3377),
                                    ElectricBlue
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Support Developer",
                        tint = Color(0xFFFF3377),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Support Solo Developer ❤️",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // User's exact requested message verbatim
                Text(
                    text = "I'm an independent developer, and your support means a lot to me. Please consider watching the ad that appears when you open the app. It helps me maintain and improve the app as a solo developer. Every view makes a difference. Thank you for your support!",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricBlue,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Watch Ad & Support Button
                GlassButton(
                    text = if (isLoadingAd) "Loading Ad..." else "Watch Ad & Support ❤️",
                    onClick = onWatchAd,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoadingAd
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary dismiss button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Continue to App",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}
