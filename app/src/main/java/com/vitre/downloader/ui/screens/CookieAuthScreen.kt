package com.vitre.downloader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitre.downloader.core.auth.CookieManager
import com.vitre.downloader.ui.components.GlassButton
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CookieAuthScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager(context) }
    val scope = rememberCoroutineScope()

    var hasCookies by remember { mutableStateOf(cookieManager.hasCookies()) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = cookieManager.importCookiesFromUri(uri)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    hasCookies = true
                    isError = false
                    importStatusMessage = "Successfully validated and imported $count session cookies."
                } else {
                    isError = true
                    importStatusMessage = result.exceptionOrNull()?.message ?: "Failed to validate cookie file."
                }
            }
        }
    }

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
                        text = "Authentication & Cookies",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Security Advisory Card
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ElectricBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Strict Client-Side Privacy",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cookies contain sensitive session credentials. Vitre Downloader never silently accesses browser cookies, never uploads them to external servers, and never includes them in error logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: Cookies do not bypass YouTube bot checks or rate limits. Use cookies only for content you are authorized to access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Status Card
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Current Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasCookies) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (hasCookies) SuccessGreen else TextTertiary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (hasCookies) "Cookies configured in private app storage" else "No cookie file active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasCookies) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Import & Clear Actions
            item {
                GlassButton(
                    text = "Import 'cookies.txt' File",
                    onClick = { filePickerLauncher.launch(arrayOf("text/plain", "*/*")) },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = OledBlack) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (hasCookies) {
                    OutlinedButton(
                        onClick = {
                            cookieManager.clearCookies()
                            hasCookies = false
                            importStatusMessage = "Cookies cleared from device."
                            isError = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Imported Cookies")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Feedback Message
            if (importStatusMessage != null) {
                item {
                    GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = if (isError) ErrorRed else SuccessGreen
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = importStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
