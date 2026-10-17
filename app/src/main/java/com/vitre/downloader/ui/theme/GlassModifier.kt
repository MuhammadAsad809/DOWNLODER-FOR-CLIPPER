package com.vitre.downloader.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Native Liquid Glass Modifier for Jetpack Compose.
 * Uses AGSL RuntimeShader on Android 13+ (API 33+) and graceful Material 3 frosted fallback on older versions.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    glassOpacity: Float = 0.12f,
    blurRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    enableAgsl: Boolean = true
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)

    val fallbackBorder = BorderStroke(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x38FFFFFF),
                Color(0x10FFFFFF)
            )
        )
    )

    val fallbackGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x1A2233),
            Color(0x0F1522)
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enableAgsl) {
        // AGSL Hardware-accelerated Shader on Android 13+
        val shaderSrc = remember {
            """
            uniform shader image;
            uniform float2 size;
            uniform float cornerRadius;
            uniform float refractionStrength;
            uniform float glareIntensity;

            float sdRoundedBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = size * 0.5;
                float2 p = fragCoord - halfSize;
                float d = sdRoundedBox(p, halfSize, cornerRadius);
                if (d > 0.0) {
                    return half4(0.0);
                }
                float edge = clamp(-d / 12.0, 0.0, 1.0);
                half4 col = image.eval(fragCoord);
                half4 tint = half4(0.05, 0.1, 0.18, 0.25);
                half4 glass = mix(col, tint, 0.35);
                float rim = (1.0 - edge) * glareIntensity;
                return glass + half4(0.0, 0.8, 1.0, 1.0) * rim * 0.3;
            }
            """.trimIndent()
        }

        val runtimeShader = remember {
            try {
                RuntimeShader(shaderSrc)
            } catch (_: Exception) {
                null
            }
        }

        if (runtimeShader != null) {
            this
                .shadow(elevation, shape)
                .clip(shape)
                .graphicsLayer {
                    runtimeShader.setFloatUniform("size", size.width, size.height)
                    runtimeShader.setFloatUniform("cornerRadius", cornerRadius.toPx())
                    runtimeShader.setFloatUniform("refractionStrength", 0.04f)
                    runtimeShader.setFloatUniform("glareIntensity", 0.5f)
                    renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "image").asComposeRenderEffect()
                }
                .border(fallbackBorder, shape)
        } else {
            // Fallback if shader creation fails
            this
                .shadow(elevation, shape)
                .clip(shape)
                .background(fallbackGradient)
                .border(fallbackBorder, shape)
        }
    } else {
        // Material 3 Frost Fallback for Android 8 - 12
        this
            .shadow(elevation, shape)
            .clip(shape)
            .background(fallbackGradient)
            .border(fallbackBorder, shape)
    }
}
