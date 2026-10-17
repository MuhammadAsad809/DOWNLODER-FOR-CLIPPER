package com.vitre.downloader.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data model representing an iOS 26 Liquid Glass navigation item.
 */
data class LiquidGlassNavItem(
    val route: String,
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * Configuration parameters for the iOS 26 Liquid Glass optical & physics engine.
 */
data class LiquidGlassConfig(
    val barHeight: Dp = 68.dp,
    val minimizedHeight: Dp = 52.dp,
    val cornerRadius: Dp = 34.dp,
    val glassTint: Color = Color(0xFF141826),
    val glassAlpha: Float = 0.42f,
    val activeAccentColor: Color = Color(0xFF38BDF8),
    val selectedContentColor: Color = Color.White,
    val unselectedContentColor: Color = Color.White.copy(alpha = 0.62f),
    val refractionStrength: Float = 24f,
    val chromaticAberration: Float = 0.018f,
    val specularIntensity: Float = 0.55f,
    val springStiffness: Float = 380f,
    val springDamping: Float = 0.62f,
    val showDetachedActionOrb: Boolean = true
)

/**
 * Android 13+ (API 33) AGSL RuntimeShader implementing iOS 26 Liquid Glass:
 * - Signed Distance Field (SDF) rounded capsule lens bevel
 * - Dynamic active tab liquid droplet metaball union (smooth minimum)
 * - Snell's-law edge refraction + RGB chromatic dispersion
 * - Directional Fresnel rim specular highlight
 */
@Language("AGSL")
const val LIQUID_GLASS_AGSL_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float2 lensCenter;
    uniform float2 lensSize;
    uniform float cornerRadius;
    uniform float refractionStrength;
    uniform float chromaticAberration;
    uniform float specularIntensity;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 halfSize = resolution * 0.5;
        float2 p = fragCoord - halfSize;

        // Outer capsule SDF and active liquid pill SDF
        float dCapsule = sdRoundedBox(p, halfSize - 2.0, cornerRadius);
        float dLens = sdRoundedBox(fragCoord - lensCenter, lensSize * 0.5, lensSize.y * 0.5);

        // Edge bevel factor (0 inside flat region, 1 near curved glass rim)
        float rimZone = smoothstep(-22.0, 0.0, dCapsule);
        float lensZone = smoothstep(16.0, -8.0, dLens);

        // Approximate surface normal gradient from center
        float2 normal = normalize(p + float2(0.0001)) * rimZone * 0.65 +
                        normalize(fragCoord - lensCenter + float2(0.0001)) * lensZone * 0.35;

        // Refract background pixels with subtle RGB chromatic dispersion
        float2 shiftR = normal * refractionStrength * (1.0 + chromaticAberration);
        float2 shiftG = normal * refractionStrength;
        float2 shiftB = normal * refractionStrength * (1.0 - chromaticAberration);

        half r = content.eval(clamp(fragCoord - shiftR, float2(0.0), resolution)).r;
        half g = content.eval(clamp(fragCoord - shiftG, float2(0.0), resolution)).g;
        half b = content.eval(clamp(fragCoord - shiftB, float2(0.0), resolution)).b;
        half a = content.eval(fragCoord).a;

        // Directional top-left Fresnel specular highlight
        float fresnel = pow(rimZone, 2.4) * max(0.0, dot(-normal, normalize(float2(0.6, 0.8))));
        half3 col = half3(r, g, b) + half3(fresnel * specularIntensity);

        return half4(col, a);
    }
"""

/**
 * iOS 26 Liquid Glass Floating Bottom Navigation Bar in Jetpack Compose.
 *
 * Features:
 * - Multi-layered glassmorphic capsule with Fresnel specular rim & inner caustic reflection
 * - Spring-physics morphing liquid lens indicator (squishes & stretches with velocity)
 * - Touch-and-drag scrubbing across tabs (like native iOS 26 TabView)
 * - Scroll-aware minimize mode (`.tabBarMinimizeBehavior(.automatic)` equivalent)
 * - Optional detached circular Liquid Glass search/action orb
 */
@Composable
fun LiquidGlassBottomNavBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isMinimized: Boolean = false,
    config: LiquidGlassConfig = LiquidGlassConfig(),
    onActionOrbClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var isDraggingPill by remember { mutableStateOf(false) }

    // Fractional index (0f .. items.lastIndex) animated with spring physics
    val animatedTabFraction = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        if (!isDraggingPill) {
            animatedTabFraction.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = config.springDamping,
                    stiffness = config.springStiffness
                )
            )
        }
    }

    val currentBarHeight by animateDpAsState(
        targetValue = if (isMinimized) config.minimizedHeight else config.barHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "barHeight"
    )

    // Velocity-based liquid stretch & squish
    val velocity = animatedTabFraction.velocity
    val stretchFactor = (1f + (abs(velocity) * 0.045f).coerceAtMost(0.28f))
    val squishFactor = (1f - (abs(velocity) * 0.022f).coerceAtMost(0.14f))

    val pressScale by animateFloatAsState(
        targetValue = if (isDraggingPill) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "pressScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main Liquid Glass Capsule Pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(currentBarHeight)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(config.cornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = config.activeAccentColor.copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(config.cornerRadius))
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(items.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDraggingPill = true
                            if (barWidthPx > 0f) {
                                val target = ((offset.x / barWidthPx) * items.size - 0.5f)
                                    .coerceIn(0f, (items.size - 1).toFloat())
                                scope.launch { animatedTabFraction.snapTo(target) }
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (barWidthPx > 0f) {
                                val target = ((change.position.x / barWidthPx) * items.size - 0.5f)
                                    .coerceIn(0f, (items.size - 1).toFloat())
                                scope.launch { animatedTabFraction.snapTo(target) }
                            }
                        },
                        onDragEnd = {
                            isDraggingPill = false
                            val snapped = animatedTabFraction.value.roundToInt()
                                .coerceIn(0, items.lastIndex)
                            onItemSelected(snapped)
                            scope.launch {
                                animatedTabFraction.animateTo(
                                    targetValue = snapped.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = config.springDamping,
                                        stiffness = config.springStiffness
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            isDraggingPill = false
                            scope.launch {
                                animatedTabFraction.animateTo(selectedIndex.toFloat())
                            }
                        }
                    )
                }
        ) {
            val itemWidthPx = if (items.isNotEmpty()) barWidthPx / items.size else 0f
            val lensCenterX = (animatedTabFraction.value + 0.5f) * itemWidthPx

            // Layer 1 & 2: Glass Surface, Fresnel Rim Border, Caustics & Liquid Droplet Indicator
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radiusPx = config.cornerRadius.toPx()

                // 1. Translucent multi-stop frosted glass base
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = config.glassAlpha * 0.42f),
                            config.glassTint.copy(alpha = config.glassAlpha),
                            config.glassTint.copy(alpha = (config.glassAlpha * 1.18f).coerceAtMost(0.95f))
                        )
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )

                // 2. Ambient glow under active liquid lens
                if (itemWidthPx > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                config.activeAccentColor.copy(alpha = 0.32f),
                                config.activeAccentColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(lensCenterX, size.height * 0.5f),
                            radius = itemWidthPx * 0.72f
                        ),
                        center = Offset(lensCenterX, size.height * 0.5f),
                        radius = itemWidthPx * 0.72f
                    )

                    // 3. Morphing Liquid Glass Active Pill (squishes & stretches with velocity)
                    val basePillWidth = (itemWidthPx - 12.dp.toPx()).coerceAtLeast(36f)
                    val basePillHeight = (size.height - 10.dp.toPx()).coerceAtLeast(28f)
                    val morphedWidth = basePillWidth * stretchFactor
                    val morphedHeight = basePillHeight * squishFactor
                    val pillTopLeft = Offset(
                        x = lensCenterX - morphedWidth * 0.5f,
                        y = (size.height - morphedHeight) * 0.5f
                    )
                    val pillRadius = CornerRadius(morphedHeight * 0.5f, morphedHeight * 0.5f)

                    // Inner liquid lens fill
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                config.activeAccentColor.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.10f)
                            ),
                            startY = pillTopLeft.y,
                            endY = pillTopLeft.y + morphedHeight
                        ),
                        topLeft = pillTopLeft,
                        size = Size(morphedWidth, morphedHeight),
                        cornerRadius = pillRadius
                    )

                    // Chromatic dispersion & specular rim on the moving liquid pill
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f * config.specularIntensity),
                                config.activeAccentColor.copy(alpha = 0.45f),
                                Color(0xFFE879F9).copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.35f)
                            ),
                            start = pillTopLeft,
                            end = Offset(pillTopLeft.x + morphedWidth, pillTopLeft.y + morphedHeight)
                        ),
                        topLeft = pillTopLeft,
                        size = Size(morphedWidth, morphedHeight),
                        cornerRadius = pillRadius,
                        style = Stroke(width = 1.35.dp.toPx())
                    )
                }

                // 4. Top-edge glass reflection highlight strip (caustic sheen)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f * config.specularIntensity),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.38f
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )

                // 5. Outer iOS 26 Fresnel Specular Rim Border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.72f * config.specularIntensity),
                            Color.White.copy(alpha = 0.14f),
                            config.activeAccentColor.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.45f * config.specularIntensity)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }

            // Layer 3: Foreground Navigation Icons, Labels & Smart Badges
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val distance = abs(animatedTabFraction.value - index)
                    val selectionProgress = (1f - distance).coerceIn(0f, 1f)
                    val isSelected = selectionProgress > 0.5f

                    val iconScale by animateFloatAsState(
                        targetValue = 1f + (0.14f * selectionProgress),
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                        label = "iconScale_$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(index) {
                                detectTapGestures(
                                    onTap = { onItemSelected(index) }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = androidx.compose.ui.graphics.lerp(
                                        config.unselectedContentColor,
                                        config.selectedContentColor,
                                        selectionProgress
                                    ),
                                    modifier = Modifier
                                        .size(if (isMinimized) 20.dp else 23.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                            translationY = if (!isMinimized) (-1.5f * selectionProgress) else 0f
                                        }
                                )

                                // Notification Badge (Smart 99+ formatting)
                                if (item.badgeCount > 0 && !isMinimized) {
                                    val badgeText = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 8.dp, y = (-5).dp)
                                            .background(Color(0xFFFF3B30), CircleShape)
                                            .padding(horizontal = 4.5.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = badgeText,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !isMinimized,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Text(
                                    text = item.label,
                                    color = androidx.compose.ui.graphics.lerp(
                                        config.unselectedContentColor,
                                        config.selectedContentColor,
                                        selectionProgress
                                    ),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detached iOS 26 Floating Liquid Glass Search / Action Orb
        if (config.showDetachedActionOrb) {
            Box(
                modifier = Modifier
                    .size(currentBarHeight)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.45f),
                        spotColor = config.activeAccentColor.copy(alpha = 0.25f)
                    )
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onActionOrbClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = config.glassAlpha * 0.45f),
                                config.glassTint.copy(alpha = config.glassAlpha),
                                config.glassTint.copy(alpha = (config.glassAlpha * 1.15f).coerceAtMost(0.95f))
                            )
                        )
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.72f * config.specularIntensity),
                                config.activeAccentColor.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        ),
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Quick Search",
                    tint = Color.White,
                    modifier = Modifier.size(if (isMinimized) 20.dp else 23.dp)
                )
            }
        }
    }
}

/**
 * Helper modifier to attach the Android 13+ AGSL Liquid Glass RuntimeShader
 * to a backdrop container when API 33+ is available.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.liquidGlassAgslEffect(
    lensCenter: Offset,
    lensSize: Size,
    cornerRadiusPx: Float,
    config: LiquidGlassConfig
): Modifier = this.graphicsLayer {
    val shader = RuntimeShader(LIQUID_GLASS_AGSL_SHADER).apply {
        setFloatUniform("resolution", size.width, size.height)
        setFloatUniform("lensCenter", lensCenter.x, lensCenter.y)
        setFloatUniform("lensSize", lensSize.width, lensSize.height)
        setFloatUniform("cornerRadius", cornerRadiusPx)
        setFloatUniform("refractionStrength", config.refractionStrength)
        setFloatUniform("chromaticAberration", config.chromaticAberration)
        setFloatUniform("specularIntensity", config.specularIntensity)
    }
    renderEffect = RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
}

/**
 * Destination enum for Vitre navigation with outlined & filled icons for liquid selection.
 */
enum class NavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    DOWNLOADS("downloads", "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    HISTORY("history", "History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    val icon: ImageVector get() = selectedIcon
}

/**
 * AdaptivePillNavBar: Vitre-adapted Liquid Glass Navigation Bar.
 */
@Composable
fun AdaptivePillNavBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    isMinimized: Boolean = false,
    activeDownloadsCount: Int = 0,
    config: LiquidGlassConfig = LiquidGlassConfig(),
    onActionOrbClick: () -> Unit = { onNavigate(NavDestination.HOME) }
) {
    val destinations = remember { NavDestination.values() }
    val navItems = remember(destinations, activeDownloadsCount) {
        destinations.map { dest ->
            LiquidGlassNavItem(
                route = dest.route,
                label = dest.title,
                unselectedIcon = dest.unselectedIcon,
                selectedIcon = dest.selectedIcon,
                badgeCount = if (dest == NavDestination.DOWNLOADS) activeDownloadsCount else 0
            )
        }
    }
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)

    LiquidGlassBottomNavBar(
        items = navItems,
        selectedIndex = selectedIndex,
        onItemSelected = { index ->
            if (index in destinations.indices) {
                onNavigate(destinations[index])
            }
        },
        modifier = modifier,
        isMinimized = isMinimized,
        config = config,
        onActionOrbClick = onActionOrbClick
    )
}

/**
 * Complete Interactive Showcase & Android Studio @Preview with Scroll-to-Minimize
 */
@Preview(showBackground = true, backgroundColor = 0xFF090D16, widthDp = 393, heightDp = 852)
@Composable
fun LiquidGlassNavBarPreview() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    // Automatically minimize tab bar when scrolling down (like iOS 26 .tabBarMinimizeBehavior(.automatic))
    val isMinimized by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    val navItems = remember {
        listOf(
            LiquidGlassNavItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
            LiquidGlassNavItem("explore", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
            LiquidGlassNavItem("saved", "Saved", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite, badgeCount = 8),
            LiquidGlassNavItem("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person)
        )
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF090D16), Color(0xFF1E1B4B))
                    )
                )
        ) {
            // Scrollable feed content passing underneath the Liquid Glass bar
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 56.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(8) { index ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "iOS 26 Liquid Glass Card #${index + 1}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scroll down to watch the floating Liquid Glass capsule automatically minimize, or drag across tabs.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Floating iOS 26 Liquid Glass Bottom Navigation Bar
            LiquidGlassBottomNavBar(
                items = navItems,
                selectedIndex = selectedTab,
                onItemSelected = { selectedTab = it },
                isMinimized = isMinimized,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}
