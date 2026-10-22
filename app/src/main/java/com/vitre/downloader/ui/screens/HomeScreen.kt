package com.vitre.downloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vitre.downloader.R
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.models.FormatOption
import com.vitre.downloader.core.models.MediaInfo
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data model for Supported Platform 3D Glass Badges
 */
data class SiteBrandItem(
    val code: String,
    val name: String,
    val color: Color,
    val drawableRes: Int,
    val secondaryColor: Color = color
)

/**
 * Resolves 3D Platform Icon for any extractor name
 */
fun getPlatformDrawable(extractor: String): Int {
    val ext = extractor.lowercase()
    return when {
        ext.contains("youtube") || ext.contains("youtu") -> R.drawable.ic_3d_youtube
        ext.contains("tiktok") -> R.drawable.ic_3d_tiktok
        ext.contains("instagram") -> R.drawable.ic_3d_instagram
        ext.contains("twitter") || ext.contains("x.com") -> R.drawable.ic_3d_x
        ext.contains("facebook") || ext.contains("fb") -> R.drawable.ic_3d_facebook
        ext.contains("pinterest") -> R.drawable.ic_3d_pinterest
        ext.contains("reddit") -> R.drawable.ic_3d_reddit
        ext.contains("twitch") -> R.drawable.ic_3d_twitch
        ext.contains("soundcloud") -> R.drawable.ic_3d_soundcloud
        ext.contains("telegram") || ext.contains("t.me") -> R.drawable.ic_3d_telegram
        ext.contains("snapchat") -> R.drawable.ic_3d_snapchat
        ext.contains("spotify") -> R.drawable.ic_3d_spotify
        ext.contains("discord") -> R.drawable.ic_3d_discord
        ext.contains("linkedin") -> R.drawable.ic_3d_linkedin
        else -> R.drawable.ic_3d_youtube
    }
}

/**
 * In-App iOS Dynamic Island Notification Data
 */
data class IosNotificationState(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val iconColor: Color = Color(0xFF00D2FF),
    val isLoading: Boolean = false,
    val actionText: String? = null,
    val onActionClick: (() -> Unit)? = null
)

/**
 * Main Home Screen with iOS Lock Screen Clock Hero, 3D Glass Logos,
 * Solid Glass Input Bar, Liquid Glass Download Button, iOS Activity Spinner,
 * Dynamic Island In-App Notifications, and iOS Springy Media Inspection Modal.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    isInspecting: Boolean,
    onInspectClick: (String) -> Unit,
    mediaInfo: MediaInfo?,
    onDismissInspection: () -> Unit,
    onStartDownload: (MediaInfo, FormatOption, Boolean, String, Long?, Long?) -> Unit,
    recentDownloads: List<DownloadEntity>,
    errorMessage: String?,
    onClearError: () -> Unit,
    onOpenCookieAuth: () -> Unit = {},
    defaultQuality: String = "best",
    defaultAudioFormat: String = "mp3",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // In-app Dynamic Island notification state
    var activeNotification by remember { mutableStateOf<IosNotificationState?>(null) }

    // Synchronize notifications with inspection and error states
    LaunchedEffect(isInspecting) {
        if (isInspecting) {
            activeNotification = IosNotificationState(
                title = "Inspecting Link...",
                subtitle = "Fetching high-resolution video streams",
                isLoading = true,
                iconColor = Color(0xFF00D2FF)
            )
        } else if (activeNotification?.isLoading == true) {
            activeNotification = null
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val isAuthRelated = errorMessage.contains("sign in", ignoreCase = true) ||
                    errorMessage.contains("bot", ignoreCase = true) ||
                    errorMessage.contains("cookie", ignoreCase = true) ||
                    errorMessage.contains("login", ignoreCase = true) ||
                    errorMessage.contains("access", ignoreCase = true) ||
                    errorMessage.contains("empty media", ignoreCase = true)

            activeNotification = IosNotificationState(
                title = if (isAuthRelated) "Authentication Required" else "Inspection Notice",
                subtitle = if (isAuthRelated) "Tap to sign in with YouTube or Instagram" else errorMessage,
                icon = if (isAuthRelated) Icons.Default.Key else Icons.Default.WarningAmber,
                iconColor = if (isAuthRelated) Color(0xFF00D2FF) else Color(0xFFFF5252),
                isLoading = false,
                actionText = if (isAuthRelated) "Sign In" else null,
                onActionClick = if (isAuthRelated) onOpenCookieAuth else null
            )
            delay(if (isAuthRelated) 8000 else 4000)
            activeNotification = null
        }
    }

    // Toggles between Hero Text, 3D Site Logos, and Features
    var heroAnimationPhase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3400)
            heroAnimationPhase = (heroAnimationPhase + 1) % 3
        }
    }

    val supportedPlatforms = remember {
        listOf(
            SiteBrandItem("YT", "YouTube", Color(0xFFFF0033), R.drawable.ic_3d_youtube, Color(0xFFFF4D6D)),
            SiteBrandItem("TT", "TikTok", Color(0xFF00F2FE), R.drawable.ic_3d_tiktok, Color(0xFF4FACFE)),
            SiteBrandItem("IG", "Instagram", Color(0xFFE1306C), R.drawable.ic_3d_instagram, Color(0xFFF77737)),
            SiteBrandItem("X", "Twitter / X", Color(0xFFFFFFFF), R.drawable.ic_3d_x, Color(0xFFAAAAAA)),
            SiteBrandItem("FB", "Facebook", Color(0xFF1877F2), R.drawable.ic_3d_facebook, Color(0xFF4292F7)),
            SiteBrandItem("PIN", "Pinterest", Color(0xFFE60023), R.drawable.ic_3d_pinterest, Color(0xFFFF334B)),
            SiteBrandItem("RD", "Reddit", Color(0xFFFF4500), R.drawable.ic_3d_reddit, Color(0xFFFF8547)),
            SiteBrandItem("TW", "Twitch", Color(0xFF9146FF), R.drawable.ic_3d_twitch, Color(0xFFB57AFF)),
            SiteBrandItem("SC", "SoundCloud", Color(0xFFFF5500), R.drawable.ic_3d_soundcloud, Color(0xFFFF8833)),
            SiteBrandItem("TG", "Telegram", Color(0xFF26A5E4), R.drawable.ic_3d_telegram, Color(0xFF5AC8FA)),
            SiteBrandItem("SNAP", "Snapchat", Color(0xFFFFFC00), R.drawable.ic_3d_snapchat, Color(0xFFFFD60A)),
            SiteBrandItem("SPOT", "Spotify", Color(0xFF1ED760), R.drawable.ic_3d_spotify, Color(0xFF30D158)),
            SiteBrandItem("DC", "Discord", Color(0xFF5865F2), R.drawable.ic_3d_discord, Color(0xFF7289DA)),
            SiteBrandItem("IN", "LinkedIn", Color(0xFF0A66C2), R.drawable.ic_3d_linkedin, Color(0xFF0077B5))
        )
    }

    val isModalVisible = mediaInfo != null
    val backgroundBlur by animateDpAsState(
        targetValue = if (isModalVisible) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "BackgroundBlur"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isModalVisible) 0.65f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrimAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030508))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isTabletOrLarge = maxWidth > 620.dp
        val contentWidthModifier = if (isTabletOrLarge) {
            Modifier.widthIn(max = 620.dp).align(Alignment.TopCenter)
        } else {
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
        }

        LazyColumn(
            modifier = contentWidthModifier
                .fillMaxSize()
                .then(
                    if (backgroundBlur > 0.dp) Modifier.blur(backgroundBlur)
                    else Modifier
                )
                .padding(horizontal = if (isTabletOrLarge) 24.dp else 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Brand Title
            item {
                Text(
                    text = "Vitre",
                    color = Color(0xFF00D2FF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // 2. iOS Dynamic Island In-App Notification (Slides down from top)
            item {
                AnimatedVisibility(
                    visible = activeNotification != null,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { -it } + fadeIn(tween(250)),
                    exit = slideOutVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) { -it } + fadeOut(tween(200))
                ) {
                    activeNotification?.let { notif ->
                        IosDynamicIslandNotification(
                            notification = notif,
                            onDismiss = { activeNotification = null }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 3. iOS Lock Screen Clock Animated Hero Section
            item {
                Spacer(modifier = Modifier.height(18.dp))
                IosClockAnimatedHero(
                    phase = heroAnimationPhase,
                    platforms = supportedPlatforms
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 4. Solid Frosted Glass Input Bar
            item {
                SolidGlassInputBar(
                    value = urlInput,
                    onValueChange = onUrlInputChange,
                    onPasteClick = {
                        clipboardManager.getText()?.text?.let { pasted ->
                            onUrlInputChange(pasted)
                            activeNotification = IosNotificationState(
                                title = "Link Pasted",
                                subtitle = pasted.take(35) + if (pasted.length > 35) "..." else "",
                                icon = Icons.Default.ContentPaste,
                                iconColor = Color(0xFF00D2FF)
                            )
                        }
                    },
                    onClearClick = {
                        onUrlInputChange("")
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 5. Liquid Glass Download / Find Media Button with iOS Activity Spinner
            item {
                LiquidGlassDownloadButton(
                    isLoading = isInspecting,
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onInspectClick(urlInput.trim())
                        } else {
                            activeNotification = IosNotificationState(
                                title = "Paste a Link",
                                subtitle = "Please enter or paste a valid media URL first",
                                icon = Icons.Default.Link,
                                iconColor = Color(0xFFFFB300)
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }

            // 6. Supported 3D Glass Platforms Row
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Supported Sources",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF5F8FC)
                        )
                        Text(
                            text = "${supportedPlatforms.size} Sites",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00D2FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(supportedPlatforms) { site ->
                            Glass3DSiteLogoCard(
                                site = site,
                                onClick = {
                                    activeNotification = IosNotificationState(
                                        title = site.name,
                                        subtitle = "4K, 1080p, Reels, Clips & Audio supported",
                                        iconColor = site.color
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 7. Recent Downloads Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Downloads",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF5F8FC)
                    )
                    if (recentDownloads.isNotEmpty()) {
                        Text(
                            text = "${recentDownloads.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E9BB0)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (recentDownloads.isEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF556275),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No downloads yet. Paste any link above to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E9BB0)
                            )
                        }
                    }
                }
            } else {
                items(recentDownloads.take(5)) { item ->
                    RecentDownloadGlassItem(item = item)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // 8. Animated Scrim Backdrop when Media Inspection is active
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismissInspection()
                    }
            )
        }

        // 9. In-Tree iOS Springy Media Inspection Modal Overlay with Background Blur
        AnimatedVisibility(
            visible = isModalVisible,
            enter = fadeIn(tween(250)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { fullHeight -> fullHeight },
            exit = fadeOut(tween(200)) + slideOutVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) { fullHeight -> fullHeight },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isTabletOrLarge) Modifier.widthIn(max = 600.dp)
                    else Modifier.fillMaxWidth()
                )
        ) {
            if (mediaInfo != null) {
                BackHandler(enabled = true) {
                    onDismissInspection()
                }
                IosSpringyMediaInspectionModal(
                    mediaInfo = mediaInfo,
                    onDismiss = onDismissInspection,
                    onConfirm = onStartDownload,
                    defaultQuality = defaultQuality,
                    defaultAudioFormat = defaultAudioFormat
                )
            }
        }
    }
}

/**
 * Authentic Apple iOS Cupertino Activity Spinner
 * Draws radial pill petals fading in sequence around a circle.
 */
@Composable
fun IosActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 22.dp,
    petalCount: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "IosSpinnerTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = petalCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "IosSpinnerPhase"
    )

    Canvas(modifier = modifier.size(size)) {
        val currentStep = phase.toInt() % petalCount
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val outerRadius = size.toPx() / 2f
        val innerRadius = size.toPx() * 0.28f
        val petalWidth = size.toPx() * 0.09f

        for (i in 0 until petalCount) {
            val angleDeg = (360f / petalCount) * i
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val distance = (currentStep - i + petalCount) % petalCount
            val alpha = (1f - (distance.toFloat() / petalCount) * 0.72f).coerceIn(0.18f, 1f)

            val startX = center.x + (innerRadius * cos(angleRad)).toFloat()
            val startY = center.y + (innerRadius * sin(angleRad)).toFloat()
            val endX = center.x + (outerRadius * cos(angleRad)).toFloat()
            val endY = center.y + (outerRadius * sin(angleRad)).toFloat()

            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = petalWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * iOS Dynamic Island In-App Notification Banner
 */
@Composable
fun IosDynamicIslandNotification(
    notification: IosNotificationState,
    onDismiss: () -> Unit
) {
    val islandShape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = islandShape,
                ambientColor = Color.Black,
                spotColor = Color(0xFF00D2FF).copy(alpha = 0.3f)
            )
            .clip(islandShape)
            .background(Color(0xFF0B0F19).copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color(0xFF00D2FF).copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = islandShape
            )
            .clickable { onDismiss() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leading Indicator: iOS Spinner or Glow Icon
            if (notification.isLoading) {
                IosActivityIndicator(
                    color = notification.iconColor,
                    size = 20.dp
                )
            } else if (notification.icon != null) {
                Icon(
                    imageVector = notification.icon,
                    contentDescription = null,
                    tint = notification.iconColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(notification.iconColor)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!notification.subtitle.isNullOrBlank()) {
                    Text(
                        text = notification.subtitle,
                        color = Color(0xFF8E9BB0),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Optional Interactive Action Button (e.g. Sign In)
            if (!notification.actionText.isNullOrBlank() && notification.onActionClick != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF00D2FF))
                        .clickable {
                            notification.onActionClick.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = notification.actionText,
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Trailing Close Button
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color(0xFF6E778A),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

/**
 * iOS Lock Screen Clock Animated Typography & 3D Wheel Rolling Hero Section
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun IosClockAnimatedHero(
    phase: Int,
    platforms: List<SiteBrandItem>
) {
    val iosClockGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFE2EDF8),
            Color(0xFF7A92AD)
        )
    )

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) { height -> height / 2 } + fadeIn(tween(400)) + scaleIn(initialScale = 0.92f))
                .togetherWith(
                    slideOutVertically(tween(320)) { height -> -height / 2 } +
                            fadeOut(tween(280)) + scaleOut(targetScale = 0.92f)
                )
        },
        label = "iOSClockHeroAnimation"
    ) { currentPhase ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (currentPhase) {
                0 -> {
                    Text(
                        text = "Download\nany video",
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            brush = iosClockGradient,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 46.sp,
                            lineHeight = 50.sp,
                            letterSpacing = (-1.5).sp,
                            shadow = Shadow(
                                color = Color(0xFF00D2FF).copy(alpha = 0.35f),
                                offset = Offset(0f, 10f),
                                blurRadius = 26f
                            )
                        )
                    )
                }
                1 -> {
                    Text(
                        text = "Save\neverything",
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            brush = iosClockGradient,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 42.sp,
                            lineHeight = 46.sp,
                            letterSpacing = (-1.2).sp,
                            shadow = Shadow(
                                color = Color(0xFF00D2FF).copy(alpha = 0.35f),
                                offset = Offset(0f, 10f),
                                blurRadius = 26f
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    HeroGlowingLogosRow(platforms.take(6))
                }
                else -> {
                    Text(
                        text = "4K • Reels\nClips • MP3",
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            brush = iosClockGradient,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 42.sp,
                            lineHeight = 46.sp,
                            letterSpacing = (-1.2).sp,
                            shadow = Shadow(
                                color = Color(0xFF00D2FF).copy(alpha = 0.35f),
                                offset = Offset(0f, 10f),
                                blurRadius = 26f
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    HeroGlowingLogosRow(platforms.drop(6).take(6))
                }
            }
        }
    }
}

/**
 * Animated row of glowing 3D Glass site discs under the Clock Typography
 */
@Composable
fun HeroGlowingLogosRow(platforms: List<SiteBrandItem>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        platforms.forEach { site ->
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                site.color.copy(alpha = 0.25f),
                                Color(0xFF141926).copy(alpha = 0.95f),
                                Color(0xFF0C101A)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                site.color.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = site.drawableRes),
                    contentDescription = site.name,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 3D Glass Site Logo Card with Depth, Specular Border, and Platform Name
 */
@Composable
fun Glass3DSiteLogoCard(
    site: SiteBrandItem,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SiteCardScale"
    )

    val cardShape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = cardShape,
                    ambientColor = site.color.copy(alpha = 0.25f),
                    spotColor = site.color.copy(alpha = 0.4f)
                )
                .clip(cardShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            site.color.copy(alpha = 0.22f),
                            Color(0xFF141B29).copy(alpha = 0.92f),
                            Color(0xFF090D14)
                        ),
                        center = Offset(28f, 15f),
                        radius = 80f
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            site.color.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = cardShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = site.drawableRes),
                contentDescription = site.name,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = site.code,
            color = Color(0xFF8E9BB0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Solid Frosted Glass Input Bar with Auto-Paste and Clear Options
 */
@Composable
fun SolidGlassInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val solidGlassShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(
                elevation = 20.dp,
                shape = solidGlassShape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(solidGlassShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF182030).copy(alpha = 0.94f),
                        Color(0xFF0E131E).copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = solidGlassShape
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Paste supported media link...",
                        color = Color(0xFF6E778A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color(0xFF00D2FF)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Animated Clear Button
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .clickable { onClearClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Solid Glass Paste Pill Button
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF00D2FF).copy(alpha = 0.14f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00D2FF).copy(alpha = 0.42f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onPasteClick() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Paste",
                    color = Color(0xFF00D2FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Liquid Glass Download Button with embedded iOS Activity Indicator
 */
@Composable
fun LiquidGlassDownloadButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "LiquidButtonScale"
    )

    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .scale(scale)
            .shadow(
                elevation = 24.dp,
                shape = pillShape,
                ambientColor = Color(0xFF00D2FF).copy(alpha = 0.45f),
                spotColor = Color(0xFF00D2FF).copy(alpha = 0.6f)
            )
            .clip(pillShape)
            .background(Color(0xFF09101C).copy(alpha = 0.95f))
            .drawWithContent {
                // Top white-cyan radial spotlight
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFF00D2FF).copy(alpha = 0.32f),
                            Color(0xFF09101C).copy(alpha = 0.0f)
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.75f
                    )
                )
                // Bottom cyan rim reflection
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00D2FF).copy(alpha = 0.2f)
                        ),
                        startY = size.height * 0.5f,
                        endY = size.height
                    )
                )
                drawContent()
            }
            .border(
                BorderStroke(
                    width = 1.3.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color(0xFF00D2FF).copy(alpha = 0.55f),
                            Color(0xFF00D2FF).copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = pillShape
            )
            .pointerInput(isLoading) {
                if (!isLoading) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                            onClick()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLoading) {
                IosActivityIndicator(
                    color = Color.White,
                    size = 22.dp
                )
                Text(
                    text = "Inspecting Media...",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Download",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * iOS Springy Media Inspection Modal with Big Half-Size Cinematic Preview,
 * Liquid Glass styling, Platform 3D Badging, Video Clipper & Format Selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSpringyMediaInspectionModal(
    mediaInfo: MediaInfo,
    onDismiss: () -> Unit,
    onConfirm: (MediaInfo, FormatOption, Boolean, String, Long?, Long?) -> Unit,
    defaultQuality: String = "best",
    defaultAudioFormat: String = "mp3"
) {
    var isAudioOnly by remember { mutableStateOf(false) }

    val isImageOnly = remember(mediaInfo) {
        mediaInfo.isImagePost || (mediaInfo.formats.any { it.isImage } && !mediaInfo.formats.any { it.isVideo })
    }
    val videoFormats = remember(mediaInfo) { mediaInfo.formats.filter { it.isVideo } }
    val audioFormats = remember(mediaInfo) { mediaInfo.formats.filter { it.isAudio && !it.isVideo } }
    val imageFormats = remember(mediaInfo) { mediaInfo.formats.filter { it.isImage } }

    var selectedAudioFormat by remember(defaultAudioFormat) { mutableStateOf(defaultAudioFormat) }

    var selectedFormat by remember(mediaInfo, defaultQuality) {
        val preferred = if (defaultQuality != "best") {
            videoFormats.firstOrNull { it.resolution.contains(defaultQuality, ignoreCase = true) }
        } else null
        mutableStateOf(
            if (isImageOnly) (imageFormats.firstOrNull() ?: mediaInfo.formats.firstOrNull())
            else (preferred ?: videoFormats.firstOrNull() ?: mediaInfo.formats.firstOrNull())
        )
    }

    // Clipper state
    var enableClipping by remember { mutableStateOf(false) }
    var clipStartText by remember { mutableStateOf("00:00") }
    var clipEndText by remember {
        val totalSec = mediaInfo.duration.takeIf { it > 0 } ?: 60L
        val defaultEnd = minOf(totalSec, 60L)
        val m = defaultEnd / 60
        val s = defaultEnd % 60
        mutableStateOf(String.format("%02d:%02d", m, s))
    }

    fun parseTimeInput(text: String): Long? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        if (clean.contains(":")) {
            val parts = clean.split(":")
            if (parts.size == 2) {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toLongOrNull() ?: return null
                return m * 60 + s
            } else if (parts.size == 3) {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toLongOrNull() ?: return null
                return h * 3600 + m * 60 + s
            }
        }
        return clean.toLongOrNull()
    }

    fun formatSecs(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    val modalShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(
                elevation = 32.dp,
                shape = modalShape,
                ambientColor = Color.Black,
                spotColor = Color(0xFF00D2FF).copy(alpha = 0.45f)
            )
            .clip(modalShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141C2B).copy(alpha = 0.92f),
                        Color(0xFF0D1422).copy(alpha = 0.95f),
                        Color(0xFF070B13).copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.3.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color(0xFF00D2FF).copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFF00D2FF).copy(alpha = 0.22f)
                        )
                    )
                ),
                shape = modalShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // 1. Top iOS Grabber Bar & Platform / Close Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val platformRes = getPlatformDrawable(mediaInfo.extractor)
                    Image(
                        painter = painterResource(id = platformRes),
                        contentDescription = mediaInfo.extractor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = mediaInfo.extractor.ifBlank { "Media Stream" },
                        color = Color(0xFF00D2FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }

                // Drag indicator pill
                Box(
                    modifier = Modifier
                        .size(38.dp, 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                )

                // Close Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Large Cinematic 16:9 Media Preview Banner (Half-card prominence)
            val bannerHeight = 185.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D1420))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color(0xFF00D2FF).copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                if (mediaInfo.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = mediaInfo.thumbnail,
                        contentDescription = mediaInfo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1E2A3E),
                                        Color(0xFF0B1019)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Vignette and bottom gradient overlay for legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Center Liquid Glass Play Disc
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.7f),
                                    Color(0xFF00D2FF).copy(alpha = 0.5f)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Top-Right "Save Thumbnail" button
                if (mediaInfo.thumbnail.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable {
                                val thumbFormat = FormatOption(
                                    formatId = mediaInfo.thumbnail,
                                    ext = "jpg",
                                    resolution = "Thumbnail (HD)",
                                    isImage = true,
                                    isVideo = false,
                                    isAudio = false
                                )
                                onConfirm(mediaInfo, thumbFormat, false, "mp3", null, null)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save Thumbnail",
                                tint = Color(0xFF00D2FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Save Thumbnail",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom-Left Quality / Type Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00D2FF).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isImageOnly) "IMAGE POST" else if (videoFormats.any { it.resolution.contains("1080") || it.resolution.contains("4K") || it.resolution.contains("2160") }) "ULTRA HD" else "HD VIDEO",
                        color = Color(0xFF00D2FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bottom-Right Duration badge
                if (mediaInfo.duration > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatSecs(mediaInfo.duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Media Title & Author details
            Text(
                text = mediaInfo.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = Color(0xFFF5F8FC),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (mediaInfo.uploader.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${mediaInfo.uploader} • ${mediaInfo.extractor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E9BB0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. iOS Segmented Control (Video vs Audio Only)
            if (!isImageOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF101726))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (!isAudioOnly) Brush.horizontalGradient(
                                    listOf(Color(0xFF00D2FF), Color(0xFF00A3FF))
                                ) else SolidColor(Color.Transparent)
                            )
                            .clickable { isAudioOnly = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Video",
                            color = if (!isAudioOnly) Color(0xFF050810) else Color(0xFF8E9BB0),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isAudioOnly) Brush.horizontalGradient(
                                    listOf(Color(0xFF00D2FF), Color(0xFF00A3FF))
                                ) else SolidColor(Color.Transparent)
                            )
                            .clickable { isAudioOnly = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Audio Only",
                            color = if (isAudioOnly) Color(0xFF050810) else Color(0xFF8E9BB0),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. Format Selection Title & Chips
            Text(
                text = when {
                    isImageOnly -> "Select Image Quality"
                    isAudioOnly -> "Select Audio Format"
                    else -> "Select Resolution"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF8E9BB0)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isImageOnly) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageFormats) { fmt ->
                        val isSelected = selectedFormat == fmt
                        QualityFormatChip(
                            label = fmt.resolution,
                            subtext = fmt.displaySize,
                            isSelected = isSelected,
                            onClick = { selectedFormat = fmt }
                        )
                    }
                }
            } else if (!isAudioOnly) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videoFormats) { fmt ->
                        val isSelected = selectedFormat == fmt
                        QualityFormatChip(
                            label = fmt.resolution,
                            subtext = fmt.displaySize,
                            isSelected = isSelected,
                            onClick = { selectedFormat = fmt }
                        )
                    }
                }
            } else {
                val formats = listOf("mp3", "m4a", "opus")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    formats.forEach { fmt ->
                        val isSelected = selectedAudioFormat == fmt
                        QualityFormatChip(
                            label = fmt.uppercase(),
                            subtext = "Audio",
                            isSelected = isSelected,
                            onClick = { selectedAudioFormat = fmt }
                        )
                    }
                }
            }

            // 5b. Download Thumbnail Quick Action
            if (mediaInfo.thumbnail.isNotBlank() && !isImageOnly) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF101726))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .clickable {
                            val thumbFormat = FormatOption(
                                formatId = mediaInfo.thumbnail,
                                ext = "jpg",
                                resolution = "Thumbnail (HD)",
                                isImage = true,
                                isVideo = false,
                                isAudio = false
                            )
                            onConfirm(mediaInfo, thumbFormat, false, "mp3", null, null)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF00D2FF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Download Thumbnail (HD)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Save high-resolution cover image to Gallery",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8E9BB0)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 6. Video / Audio Clipper / Trimmer Controls
            if (!isImageOnly) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF141C2B).copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = if (enableClipping) Color(0xFF00D2FF) else Color(0xFF8E9BB0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAudioOnly) "Clip / Trim Audio" else "Clip / Trim Video",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (enableClipping) Color(0xFF00D2FF) else Color(0xFFF5F8FC)
                                )
                            }
                            Switch(
                                checked = enableClipping,
                                onCheckedChange = { enableClipping = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Color(0xFF00D2FF),
                                    uncheckedThumbColor = Color(0xFF556275),
                                    uncheckedTrackColor = Color(0xFF0A0F17)
                                )
                            )
                        }

                        if (enableClipping) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start Time (MM:SS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8E9BB0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicTextField(
                                        value = clipStartText,
                                        onValueChange = { clipStartText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF00D2FF)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End Time (MM:SS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8E9BB0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicTextField(
                                        value = clipEndText,
                                        onValueChange = { clipEndText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF00D2FF)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            val parsedStart = parseTimeInput(clipStartText) ?: 0L
                            val parsedEnd = parseTimeInput(clipEndText) ?: 0L
                            val clipDuration = (parsedEnd - parsedStart).coerceAtLeast(0L)

                            Spacer(modifier = Modifier.height(10.dp))
                            // Quick duration presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf("30s" to 30L, "1m" to 60L, "2m" to 120L, "5m" to 300L)
                                presets.forEach { (label, secs) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                clipStartText = "00:00"
                                                clipEndText = formatSecs(secs)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color(0xFF00D2FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (parsedEnd > parsedStart) {
                                    "Selected Duration: ${clipDuration / 60}m ${clipDuration % 60}s (downloads only this segment)"
                                } else {
                                    "End time must be greater than start time"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (parsedEnd > parsedStart) Color(0xFF00D2FF) else Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Action Buttons: Cancel & Start Download
            val parsedStart = if (enableClipping) parseTimeInput(clipStartText) else null
            val parsedEnd = if (enableClipping) parseTimeInput(clipEndText) else null
            val canDownload = !enableClipping || (parsedStart != null && parsedEnd != null && parsedEnd > parsedStart)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF8E9BB0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (canDownload) Brush.horizontalGradient(
                                listOf(Color(0xFF00D2FF), Color(0xFF0091FF))
                            ) else SolidColor(Color(0xFF1E2838))
                        )
                        .border(
                            1.dp,
                            if (canDownload) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = canDownload) {
                            val fmt = if (isAudioOnly) {
                                audioFormats.firstOrNull { it.ext.equals(selectedAudioFormat, ignoreCase = true) }
                                    ?: audioFormats.firstOrNull()
                                    ?: FormatOption(
                                        formatId = "bestaudio",
                                        ext = selectedAudioFormat,
                                        resolution = "Audio (${selectedAudioFormat.uppercase()})",
                                        isAudio = true,
                                        isVideo = false
                                    )
                            } else {
                                selectedFormat ?: mediaInfo.formats.first()
                            }
                            onConfirm(mediaInfo, fmt, isAudioOnly, selectedAudioFormat, parsedStart, parsedEnd)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (enableClipping && parsedStart != null && parsedEnd != null && parsedEnd > parsedStart) {
                            val d = parsedEnd - parsedStart
                            "Download Clip (${d / 60}m ${d % 60}s)"
                        } else {
                            "Start Download"
                        },
                        color = if (canDownload) Color.Black else Color(0xFF556275),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Format selection chip with resolution and filesize
 */
@Composable
fun QualityFormatChip(
    label: String,
    subtext: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(
                if (isSelected) Color(0xFF00D2FF).copy(alpha = 0.18f)
                else Color(0xFF141C2B)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.08f),
                shape = chipShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (isSelected) Color(0xFF00D2FF) else Color(0xFFF5F8FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                color = Color(0xFF8E9BB0),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Recent download item in Liquid Glass Card styling
 */
@Composable
fun RecentDownloadGlassItem(item: DownloadEntity) {
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(cardShape)
            .background(Color(0xFF0F1522).copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = cardShape
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161F30)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF00D2FF)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color(0xFFF5F8FC),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.resolution,
                        color = Color(0xFF00D2FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "•",
                        color = Color(0xFF556275),
                        fontSize = 12.sp
                    )
                    Text(
                        text = item.status,
                        color = if (item.status.contains("PAUSED", ignoreCase = true)) Color(0xFFFFB300) else Color(0xFF8E9BB0),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.status != "COMPLETED" && item.progress in 0.01f..0.99f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (item.status.contains("PAUSED", ignoreCase = true)) Color(0xFFFFB300) else Color(0xFF00D2FF),
                        trackColor = Color(0xFF161F30)
                    )
                }
            }
        }
    }
}

/**
 * Public VitreHomeScreen alias for standalone invocation or design preview
 */
@Composable
fun VitreHomeScreen(
    onDownloadClick: (String) -> Unit = {}
) {
    var urlText by remember { mutableStateOf("") }
    HomeScreen(
        urlInput = urlText,
        onUrlInputChange = { urlText = it },
        isInspecting = false,
        onInspectClick = onDownloadClick,
        mediaInfo = null,
        onDismissInspection = {},
        onStartDownload = { _, _, _, _, _, _ -> },
        recentDownloads = emptyList(),
        errorMessage = null,
        onClearError = {}
    )
}
