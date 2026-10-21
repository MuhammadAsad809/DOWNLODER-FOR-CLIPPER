package com.vitre.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager as AndroidCookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    // Active in-app full-screen browser target (null when closed)
    var activeWebTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Dialog state for manually pasting cookies
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteDomain by remember { mutableStateOf("https://www.youtube.com/") }
    var pasteContent by remember { mutableStateOf("") }

    // Dialog state for opening custom website in in-app browser
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("https://") }

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
                    importStatusMessage = "Successfully imported and validated $count session cookies."
                } else {
                    isError = true
                    importStatusMessage = result.exceptionOrNull()?.message ?: "Failed to validate cookie file."
                }
            }
        }
    }

    // Intercept hardware back button when in-app browser is active
    BackHandler(enabled = activeWebTarget != null) {
        activeWebTarget = null
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
                                text = if (hasCookies) "Cookies Active & In Use" else "No Session Cookies Configured",
                                color = if (hasCookies) SuccessGreen else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (activeDomains.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Authenticated Domains:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                activeDomains.take(4).forEach { domain ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF131D2E),
                                        border = BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF00D2FF),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (hasCookies) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    cookieManager.clearCookies()
                                    hasCookies = false
                                    activeDomains = emptyList()
                                    importStatusMessage = "All session cookies cleared."
                                    isError = false
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear All Stored Cookies", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Notification / Status Message
            if (importStatusMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isError) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isError) ErrorRed.copy(alpha = 0.4f) else SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) ErrorRed else SuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = importStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // In-App One-Tap Sign In Section
            item {
                Text(
                    text = "One-Tap In-App Authentication",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E9BB0)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 1. YouTube & Google Account (Gmail) In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In with Google / YouTube",
                    subtitle = "Log into Gmail/YouTube in the in-app browser, then tap Done to save session",
                    iconRes = R.drawable.ic_3d_youtube,
                    accentColor = Color(0xFFFF0033),
                    badgeText = "In-App Browser",
                    onClick = {
                        activeWebTarget = Pair(
                            "Google & YouTube Sign-In",
                            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fm.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Instagram In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to Instagram",
                    subtitle = "Log into Instagram in the in-app browser, then tap Done to save session",
                    iconRes = R.drawable.ic_3d_instagram,
                    accentColor = Color(0xFFE1306C),
                    badgeText = "In-App Browser",
                    onClick = {
                        activeWebTarget = Pair("Instagram In-App Login", "https://www.instagram.com/accounts/login/")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Reddit In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to Reddit",
                    subtitle = "Log into Reddit in the in-app browser, then tap Done to save session",
                    iconRes = R.drawable.ic_3d_reddit,
                    accentColor = Color(0xFFFF4500),
                    badgeText = "In-App Browser",
                    onClick = {
                        activeWebTarget = Pair("Reddit In-App Login", "https://www.reddit.com/login/")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. TikTok In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to TikTok",
                    subtitle = "Log into TikTok in the in-app browser, then tap Done to save session",
                    iconRes = R.drawable.ic_3d_tiktok,
                    accentColor = Color(0xFF00D2FF),
                    badgeText = "In-App Browser",
                    onClick = {
                        activeWebTarget = Pair("TikTok In-App Login", "https://m.tiktok.com/login/")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. X / Twitter In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to Twitter / X",
                    subtitle = "Log into X in the in-app browser, then tap Done to save session",
                    iconRes = R.drawable.ic_3d_x,
                    accentColor = Color(0xFF1DA1F2),
                    badgeText = "In-App Browser",
                    onClick = {
                        activeWebTarget = Pair("Twitter / X In-App Login", "https://x.com/i/flow/login")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 6. Custom Website (YTDLnis-style any site)
            item {
                PlatformAuthCard(
                    title = "Custom Website / Any Domain",
                    subtitle = "Open any site (Bilibili, Facebook, Dailymotion, etc.) to log in",
                    iconVector = Icons.Default.Language,
                    accentColor = Color(0xFF00E676),
                    badgeText = "In-App Browser",
                    onClick = {
                        showCustomUrlDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Manual Input & File Management
            item {
                Text(
                    text = "Manual Cookie Import",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E9BB0)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Paste Cookies Directly
            item {
                PlatformAuthCard(
                    title = "Paste Cookie String or Netscape Text",
                    subtitle = "Directly paste cookie header string or cookies.txt content",
                    iconVector = Icons.Default.ContentPaste,
                    accentColor = Color(0xFF9D4EDD),
                    onClick = {
                        showPasteDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. Import cookies.txt file
            item {
                PlatformAuthCard(
                    title = "Import Netscape cookies.txt File",
                    subtitle = "Select a cookies.txt exported from Chrome / Firefox extensions",
                    iconVector = Icons.Default.FileOpen,
                    accentColor = Color(0xFF3B82F6),
                    onClick = {
                        filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Full-Screen In-App Web Extractor (No Dialog = 100% full screen, reliable touch & layout)
        if (activeWebTarget != null) {
            val (targetTitle, targetUrl) = activeWebTarget!!
            InAppBrowserOverlay(
                initialUrl = targetUrl,
                title = targetTitle,
                onDismiss = { activeWebTarget = null },
                onCookiesExtracted = { url, cookieHeader, isVerified ->
                    scope.launch {
                        val res = cookieManager.saveCookiesFromWeb(url, cookieHeader)
                        activeWebTarget = null
                        if (res.isSuccess) {
                            val count = res.getOrNull() ?: 0
                            hasCookies = true
                            activeDomains = cookieManager.getActiveDomains()
                            isError = false
                            importStatusMessage = if (isVerified) {
                                "✓ Successfully authenticated and saved $count session cookies for $targetTitle!"
                            } else {
                                "Saved $count cookies (unverified account session) for $targetTitle."
                            }
                        } else {
                            isError = true
                            importStatusMessage = res.exceptionOrNull()?.message ?: "Failed to save session cookies."
                        }
                    }
                }
            )
        }

        // Paste Cookies Modal Dialog
        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Paste Cookie String", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Paste your cookie header (name=val; name2=val2) or full Netscape cookies.txt format:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E9BB0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pasteDomain,
                            onValueChange = { pasteDomain = it },
                            label = { Text("Target Domain / URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasteContent,
                            onValueChange = { pasteContent = it },
                            label = { Text("Cookie Content") },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            maxLines = 8
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pasteContent.isNotBlank()) {
                                scope.launch {
                                    val res = cookieManager.saveCookiesFromWeb(pasteDomain.trim(), pasteContent.trim())
                                    showPasteDialog = false
                                    if (res.isSuccess) {
                                        val count = res.getOrNull() ?: 0
                                        hasCookies = true
                                        activeDomains = cookieManager.getActiveDomains()
                                        isError = false
                                        importStatusMessage = "Successfully parsed and saved $count cookies."
                                    } else {
                                        isError = true
                                        importStatusMessage = res.exceptionOrNull()?.message ?: "Failed to parse cookies."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color.Black)
                    ) {
                        Text("Save Cookies", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) {
                        Text("Cancel", color = Color(0xFF8E9BB0))
                    }
                },
                containerColor = Color(0xFF0D1422),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Custom URL Input Modal Dialog
        if (showCustomUrlDialog) {
            AlertDialog(
                onDismissRequest = { showCustomUrlDialog = false },
                title = { Text("Open Custom Website", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter any website or login URL (e.g., https://bilibili.com, https://facebook.com):",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E9BB0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("Website Login URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            var u = customUrlInput.trim()
                            if (u.isNotBlank()) {
                                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                                    u = "https://$u"
                                }
                                val domainHost = try {
                                    Uri.parse(u).host ?: u
                                } catch (_: Exception) { u }
                                showCustomUrlDialog = false
                                activeWebTarget = Pair(domainHost, u)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color.Black)
                    ) {
                        Text("Open Browser", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomUrlDialog = false }) {
                        Text("Cancel", color = Color(0xFF8E9BB0))
                    }
                },
                containerColor = Color(0xFF0D1422),
                shape = RoundedCornerShape(16.dp)
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
    badgeText: String? = null,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
 * Full-Screen In-App Browser Overlay inspired by YTDLnis and Seal.
 * Features:
 * - Real User-Agent switching (Desktop Chrome for Google auth / Mobile for standard sites)
 * - Navigation history (Back / Forward / Reload) and native BackHandler
 * - Live authenticated session verification (checks actual session tokens, zero false positives)
 * - Clear cookies / session reset
 * - External app intent suppression (prevents YouTube/Instagram apps from opening)
 */
@Composable
fun InAppBrowserOverlay(
    initialUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onCookiesExtracted: (url: String, cookieHeader: String, isVerified: Boolean) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(title) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showNotLoggedInDialog by remember { mutableStateOf(false) }
    var showClearCookiesDialog by remember { mutableStateOf(false) }
    var cookiesUpdatedSignal by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cookieManager = remember { CookieManager(context) }

    val isGoogleOrYouTube = remember(initialUrl) {
        initialUrl.contains("youtube.com") || initialUrl.contains("google.com")
    }

    val desktopUserAgent = remember {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
    val mobileUserAgent = remember {
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    var isDesktopMode by remember {
        mutableStateOf(isGoogleOrYouTube)
    }

    // Hardware back button navigates WebView history first
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onDismiss()
        }
    }

    fun collectAllCookies(activeUrl: String, cm: AndroidCookieManager): String {
        cm.flush()
        val primaryCookies = cm.getCookie(activeUrl) ?: ""
        return buildString {
            append(primaryCookies)
            if (activeUrl.contains("google.com") || activeUrl.contains("youtube.com")) {
                val c1 = cm.getCookie("https://www.youtube.com/") ?: ""
                val c2 = cm.getCookie("https://m.youtube.com/") ?: ""
                val c3 = cm.getCookie("https://accounts.google.com/") ?: ""
                val c4 = cm.getCookie("https://www.google.com/") ?: ""
                if (c1.isNotBlank() && !contains(c1)) append("; ").append(c1)
                if (c2.isNotBlank() && !contains(c2)) append("; ").append(c2)
                if (c3.isNotBlank() && !contains(c3)) append("; ").append(c3)
                if (c4.isNotBlank() && !contains(c4)) append("; ").append(c4)
            } else if (activeUrl.contains("instagram.com")) {
                val c1 = cm.getCookie("https://www.instagram.com/") ?: ""
                val c2 = cm.getCookie("https://i.instagram.com/") ?: ""
                if (c1.isNotBlank() && !contains(c1)) append("; ").append(c1)
                if (c2.isNotBlank() && !contains(c2)) append("; ").append(c2)
            } else if (activeUrl.contains("reddit.com")) {
                val c1 = cm.getCookie("https://www.reddit.com/") ?: ""
                if (c1.isNotBlank() && !contains(c1)) append("; ").append(c1)
            } else if (activeUrl.contains("tiktok.com")) {
                val c1 = cm.getCookie("https://www.tiktok.com/") ?: ""
                val c2 = cm.getCookie("https://m.tiktok.com/") ?: ""
                if (c1.isNotBlank() && !contains(c1)) append("; ").append(c1)
                if (c2.isNotBlank() && !contains(c2)) append("; ").append(c2)
            } else if (activeUrl.contains("x.com") || activeUrl.contains("twitter.com")) {
                val c1 = cm.getCookie("https://x.com/") ?: ""
                val c2 = cm.getCookie("https://twitter.com/") ?: ""
                if (c1.isNotBlank() && !contains(c1)) append("; ").append(c1)
                if (c2.isNotBlank() && !contains(c2)) append("; ").append(c2)
            }
        }
    }

    val isAuthDetected = remember(currentUrl, cookiesUpdatedSignal) {
        val cm = AndroidCookieManager.getInstance()
        val c = collectAllCookies(currentUrl, cm)
        cookieManager.hasAuthSession(currentUrl, c)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030508))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation & Action Header
            Surface(
                color = Color(0xFF0D1422),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Navigation Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) Color.White else Color(0xFF4A5568)
                            )
                        }
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) Color.White else Color(0xFF4A5568)
                            )
                        }
                        IconButton(onClick = {
                            pageError = null
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                        }
                    }

                    // Right Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Desktop / Mobile Mode Toggle
                        IconButton(onClick = {
                            isDesktopMode = !isDesktopMode
                            webViewRef?.settings?.userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                            webViewRef?.reload()
                        }) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = if (isDesktopMode) "Desktop Mode" else "Mobile Mode",
                                tint = if (isDesktopMode) Color(0xFF00D2FF) else Color(0xFF8E9BB0)
                            )
                        }

                        // Clear WebView Session Cookies
                        IconButton(onClick = { showClearCookiesDialog = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear Browser Session",
                                tint = Color(0xFF8E9BB0)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Done / Save Cookies Button
                        Button(
                            onClick = {
                                val cm = AndroidCookieManager.getInstance()
                                val allCookies = collectAllCookies(currentUrl, cm)
                                val verified = cookieManager.hasAuthSession(currentUrl, allCookies)
                                if (verified) {
                                    onCookiesExtracted(currentUrl, allCookies, true)
                                } else {
                                    showNotLoggedInDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAuthDetected) Color(0xFF00E676) else Color(0xFF00D2FF),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isAuthDetected) Icons.Default.CheckCircle else Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAuthDetected) "Save Session" else "Done",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Real-time Auth Status & URL Sub-Bar
            Surface(
                color = Color(0xFF101726),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E9BB0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAuthDetected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFA500).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isAuthDetected) Color(0xFF00E676) else Color(0xFFFFA500).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isAuthDetected) "✓ Logged In" else "○ Sign-in needed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isAuthDetected) Color(0xFF00E676) else Color(0xFFFFA500),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { if (progress > 0f) progress else 0.1f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00D2FF),
                    trackColor = Color(0xFF101726)
                )
            }

            // Error View if loading failed
            if (pageError != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF080D17)).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Page Loading Failed", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(pageError ?: "", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E9BB0))
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                pageError = null
                                webViewRef?.loadUrl(currentUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color.Black)
                        ) {
                            Text("Retry Connection", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Main Native Android WebView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowContentAccess = true
                                allowFileAccess = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(false)
                            }

                            val cookieMgr = AndroidCookieManager.getInstance()
                            cookieMgr.setAcceptCookie(true)
                            cookieMgr.setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progress = newProgress / 100f
                                    isLoading = newProgress < 100
                                    if (newProgress == 100) {
                                        cookiesUpdatedSignal++
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    pageError = null
                                    url?.let {
                                        currentUrl = it
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        cookiesUpdatedSignal++
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let {
                                        currentUrl = it
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        cookiesUpdatedSignal++
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    url?.let {
                                        currentUrl = it
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        cookiesUpdatedSignal++
                                    }
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        pageError = error?.description?.toString() ?: "Failed to connect to page."
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false

                                    // BLOCK external app hijacking (prevents YouTube app and Instagram app from opening!)
                                    if (reqUrl.startsWith("intent://") ||
                                        reqUrl.startsWith("vnd.youtube:") ||
                                        reqUrl.startsWith("instagram://") ||
                                        reqUrl.startsWith("market://") ||
                                        reqUrl.startsWith("snssdk1233:") ||
                                        reqUrl.startsWith("snssdk1180:") ||
                                        reqUrl.startsWith("twitter:") ||
                                        reqUrl.startsWith("android-app://")) {
                                        try {
                                            val parsed = Intent.parseUri(reqUrl, Intent.URI_INTENT_SCHEME)
                                            val fallback = parsed.getStringExtra("browser_fallback_url")
                                            if (!fallback.isNullOrBlank()) {
                                                view?.loadUrl(fallback)
                                            }
                                        } catch (_: Exception) {}
                                        return true // Suppress intent from launching external app
                                    }

                                    return false // Allow WebView to navigate inside app
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

        // Dialog: Not Logged In Confirmation Warning
        if (showNotLoggedInDialog) {
            AlertDialog(
                onDismissRequest = { showNotLoggedInDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA500))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Not Logged In Yet", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                text = {
                    Text(
                        text = "You are currently still on the sign-in page or haven't finished logging into your account.\n\nTo download restricted, private, or high-quality media, please log into your account first, then tap Save Session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAC7D5)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showNotLoggedInDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color.Black)
                    ) {
                        Text("Continue Sign-In", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotLoggedInDialog = false
                            val cm = AndroidCookieManager.getInstance()
                            val allCookies = collectAllCookies(currentUrl, cm)
                            onCookiesExtracted(currentUrl, allCookies, false)
                        }
                    ) {
                        Text("Save Anyway (Guest)", color = Color(0xFF8E9BB0))
                    }
                },
                containerColor = Color(0xFF0D1422),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Dialog: Clear WebView Cookies
        if (showClearCookiesDialog) {
            AlertDialog(
                onDismissRequest = { showClearCookiesDialog = false },
                title = { Text("Clear Browser Cookies?", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Text(
                        "This will wipe all cookies and log you out of the in-app browser so you can log in fresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAC7D5)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearCookiesDialog = false
                            val cm = AndroidCookieManager.getInstance()
                            cm.removeAllCookies {
                                cm.flush()
                                cookiesUpdatedSignal++
                                webViewRef?.reload()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                    ) {
                        Text("Clear & Reload", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCookiesDialog = false }) {
                        Text("Cancel", color = Color(0xFF8E9BB0))
                    }
                },
                containerColor = Color(0xFF0D1422),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
