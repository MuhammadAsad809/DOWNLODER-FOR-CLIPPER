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

            // 1. YouTube In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to YouTube",
                    subtitle = "Opens mobile YouTube inside Vitre — stays in app, captures session cookies",
                    iconRes = R.drawable.ic_3d_youtube,
                    accentColor = Color(0xFFFF0033),
                    badgeText = "In-App",
                    onClick = {
                        activeWebTarget = Pair("YouTube In-App Login", "https://m.youtube.com/")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Instagram In-App Login
            item {
                PlatformAuthCard(
                    title = "Sign In to Instagram",
                    subtitle = "Opens Instagram web login inside Vitre — extracts full session",
                    iconRes = R.drawable.ic_3d_instagram,
                    accentColor = Color(0xFFE1306C),
                    badgeText = "In-App",
                    onClick = {
                        activeWebTarget = Pair("Instagram In-App Login", "https://www.instagram.com/accounts/login/")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. TikTok / Other Sites
            item {
                PlatformAuthCard(
                    title = "Sign In to TikTok / Other",
                    subtitle = "Extract cookies for TikTok, X / Twitter, or other sites",
                    iconVector = Icons.Default.Language,
                    accentColor = Color(0xFF00D2FF),
                    badgeText = "In-App",
                    onClick = {
                        activeWebTarget = Pair("TikTok In-App Login", "https://m.tiktok.com/")
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
                onCookiesExtracted = { url, cookieHeader ->
                    scope.launch {
                        val res = cookieManager.saveCookiesFromWeb(url, cookieHeader)
                        activeWebTarget = null
                        if (res.isSuccess) {
                            val count = res.getOrNull() ?: 0
                            hasCookies = true
                            activeDomains = cookieManager.getActiveDomains()
                            isError = false
                            importStatusMessage = "Successfully captured $count session cookies for $targetTitle!"
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
 * Full-Screen In-App Browser Overlay with Intent-blocking and real-time cookie extraction.
 * Guarantees zero external app hijack (YouTube/Instagram app will NEVER open).
 */
@Composable
fun InAppBrowserOverlay(
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
    var pageError by remember { mutableStateOf<String?>(null) }

    val isYouTube = remember { initialUrl.contains("youtube.com") }
    val isInstagram = remember { initialUrl.contains("instagram.com") }

    val userAgent = remember {
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
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
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pageTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                        IconButton(onClick = {
                            pageError = null
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                        }

                        Button(
                            onClick = {
                                val cm = AndroidCookieManager.getInstance()
                                val primaryCookies = cm.getCookie(currentUrl) ?: ""
                                val allCookies = buildString {
                                    append(primaryCookies)
                                    if (isYouTube) {
                                        val c1 = cm.getCookie("https://www.youtube.com/") ?: ""
                                        val c2 = cm.getCookie("https://accounts.google.com/") ?: ""
                                        if (c1.isNotBlank() && c1 != primaryCookies) append("; ").append(c1)
                                        if (c2.isNotBlank() && c2 != primaryCookies) append("; ").append(c2)
                                    } else if (isInstagram) {
                                        val c1 = cm.getCookie("https://www.instagram.com/") ?: ""
                                        val c2 = cm.getCookie("https://i.instagram.com/") ?: ""
                                        if (c1.isNotBlank() && c1 != primaryCookies) append("; ").append(c1)
                                        if (c2.isNotBlank() && c2 != primaryCookies) append("; ").append(c2)
                                    }
                                }
                                onCookiesExtracted(currentUrl, allCookies)
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
                                userAgentString = userAgent
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
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    pageError = null
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let {
                                        currentUrl = it
                                        val header = cookieMgr.getCookie(it) ?: ""
                                        val count = if (header.isBlank()) 0 else header.split(";").count { c -> c.trim().contains("=") }
                                        detectedCookiesCount = count
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
    }
}
