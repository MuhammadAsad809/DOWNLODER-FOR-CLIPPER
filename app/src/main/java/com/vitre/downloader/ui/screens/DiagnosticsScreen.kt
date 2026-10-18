package com.vitre.downloader.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitre.downloader.core.downloader.FFmpegEngine
import com.vitre.downloader.core.downloader.YtDlpEngine
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*

@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ytDlpEngine = remember { YtDlpEngine() }
    val ffmpegEngine = remember { FFmpegEngine() }

    val (ytdlpVer, pythonVer) = remember { ytDlpEngine.getEngineVersions() }
    val ffmpegVer = remember { ffmpegEngine.getFFmpegVersion() }

    Box(modifier = modifier.fillMaxSize().background(OledBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Engine Diagnostics",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Engine Status Card
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Native Runtime Status",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        DiagnosticRow(label = "Application Version", value = "1.0.0 (Native)")
                        DiagnosticRow(label = "Android OS", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        DiagnosticRow(label = "Device Architecture", value = Build.SUPPORTED_ABIS.joinToString(", "))
                        DiagnosticRow(label = "yt-dlp Engine", value = ytdlpVer)
                        DiagnosticRow(label = "Media Processing Stack", value = ffmpegVer)
                        DiagnosticRow(label = "Python Runtime", value = pythonVer.lines().firstOrNull() ?: pythonVer)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Architecture Verification
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = ElectricBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Privacy & Security Assurances",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "• Zero remote proxy servers: Media downloads occur directly between your device and target servers; if anti-bot walls block TikTok, fallback metadata resolves the direct CDN URL.\n• Local SQLite storage: History and download records are saved only in the device sandbox.\n• Sanitized diagnostics: No URLs, credentials, or cookies are stored or exposed in diagnostic outputs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary
        )
    }
}
