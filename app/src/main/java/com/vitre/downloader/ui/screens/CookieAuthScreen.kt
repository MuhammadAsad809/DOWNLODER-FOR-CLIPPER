package com.vitre.downloader.ui.screens

import android.net.Uri
import android.webkit.CookieManager as AndroidCookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitre.downloader.R
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
    var activeDomains by remember { mutableStateOf(cookieManager.getActiveDomains()) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Active in-app web extractor target: Pair(title, url)
    var webExtractorTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = cookieManager.importCookiesFromUri(uri)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    hasCookies = true
                    activeDomains = cookieManager.getActiveDomains()
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
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Status Card
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Current Authentication Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasCookies) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (hasCookies) SuccessGreen else TextTertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (hasCookies) "Session cookies active in app storage" else "No session cookies active",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (hasCookies) TextPrimary else TextSecondary
                            )
                        }

                        if (activeDomains.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Authenticated Platforms: ${activeDomains.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricBlue
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Automatic In-App Cookie Extractors
            item {
                Text(
                    text = "Automatic In-App Extractors",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E9BB0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in directly inside Vitre to bypass YouTube bot blocks and access restricted Instagram reels. Cookies are automatically captured and saved client-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // YouTube Extractor Button
            item {
                PlatformAuthCard(
                    title = "Sign In to YouTube / Google",
                    subtitle = "Bypasses 'Sign in to confirm you're not a bot' blocks",
                    iconRes = R.drawable.ic_3d_youtube,
                    accentColor = Color(0xFFFF0033),
                    onClick = {
                        webExtractorTarget = Pair(
                            "YouTube / Google Authentication",
                            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Instagram Extractor Button
            item {
                PlatformAuthCard(
                    title = "Sign In to Instagram",
                    subtitle = "Enables downloading private or restricted Instagram reels",
                    iconRes = R.drawable.ic_3d_instagram,
                    accentColor = Color(0xFFE1306C),
                    onClick = {
                        webExtractorTarget = Pair(
                            "Instagram Authentication",
                            "https://www.instagram.com/accounts/login/"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Generic / Other Sites Extractor
            item {
                PlatformAuthCard(
                    title = "Sign In to Other Platform",
                    subtitle = "Extract cookies for TikTok, Twitter / X, or custom sites",
                    iconVector = Icons.Default.Language,
                    accentColor = Color(0xFF00D2FF),
                    onClick = {
                        webExtractorTarget = Pair(
                            "Web Session Cookie Extractor",
                            "https://m.tiktok.com/"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Manual Import & Management
            item {
                Text(
                    text = "Manual Management",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E9BB0)
                )
                Spacer(modifier = Modifier.height(12.dp))

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
                            activeDomains = emptyList()
                            importStatusMessage = "All session cookies cleared from device."
                            isError = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Stored Session Cookies")
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

        // In-App Web Cookie Extractor Dialog Modal
        if (webExtractorTarget != null) {
            val (targetTitle, targetUrl) = webExtractorTarget!!
            InAppCookieExtractorModal(
                initialUrl = targetUrl,
                title = targetTitle,
                onDismiss = { webExtractorTarget = null },
                onCookiesExtracted = { url, cookieHeader ->
                    scope.launch {
                        val res = cookieManager.saveCookiesFromWeb(url, cookieHeader)
                        webExtractorTarget = null
                        if (res.isSuccess) {
                            val count = res.getOrNull() ?: 0
                            hasCookies = true
                            activeDomains = cookieManager.getActiveDomains()
                            isError = false
                            importStatusMessage = "Successfully captured and merged session cookies ($count cookies active)."
                        } else {
                            isError = true
                            importStatusMessage = res.exceptionOrNull()?.message ?: "Failed to save session cookies."
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PlatformAuthCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    iconRes: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F1624))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E9BB0)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF8E9BB0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Authentic In-App Browser Dialog with Live Cookie Detection and 1-Tap Netscape Export.
 */
@Composable
fun InAppCookieExtractorModal(
    initialUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onCookiesExtracted: (String, String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(title) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var detectedCookiesCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030508))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navigation Bar
                Surface(
                    color = Color(0xFF0D1422),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = pageTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF00D2FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                            }

                            Button(
                                onClick = {
                                    val cookieHeader = AndroidCookieManager.getInstance().getCookie(currentUrl) ?: ""
                                    onCookiesExtracted(currentUrl, cookieHeader)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00D2FF),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (detectedCookiesCount > 0) "Save ($detectedCookiesCount)" else "Save Cookies",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00D2FF),
                        trackColor = Color(0xFF101726)
                    )
                }

                // WebView Container
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                loadWithOverviewMode = true
                                useWideViewPort = true
                            }
                            val cookieMgr = AndroidCookieManager.getInstance()
                            cookieMgr.setAcceptCookie(true)
                            cookieMgr.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let {
                                        currentUrl = it
                                        val titleText = view?.title ?: pageTitle
                                        if (titleText.isNotBlank()) pageTitle = titleText

                                        val header = cookieMgr.getCookie(it) ?: ""
                                        val count = if (header.isBlank()) 0 else header.split(";").count { c -> c.trim().contains("=") }
                                        detectedCookiesCount = count
                                    }
                                }
                            }
                            loadUrl(initialUrl)
                        }
                    },
                    update = {
                        webViewRef = it
                    }
                )
            }
        }
    }
}
