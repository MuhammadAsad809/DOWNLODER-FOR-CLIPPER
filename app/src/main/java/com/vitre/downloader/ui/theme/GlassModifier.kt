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

object GlassShaderCache {
    private const val SHADER_SRC = """
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
            half4 solidDark = half4(0.02, 0.03, 0.05, 0.88);
            half4 glass = mix(col, solidDark, 0.85);
            float rim = (1.0 - edge) * glareIntensity;
            return glass + half4(0.0, 0.85, 1.0, 1.0) * rim * 0.35;
        }
    """

    val runtimeShader: RuntimeShader? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(SHADER_SRC.trimIndent())
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
}

/**
 * Native Solid Glass Black Modifier for Jetpack Compose.
 * Delivers a deep obsidian glass appearance with silky-smooth 60/120fps hardware rendering.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 4.dp,
    enableAgsl: Boolean = false
): Modifier = composed {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val solidGlassBorder = remember(borderWidth) {
        BorderStroke(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x38FFFFFF),
                    Color(0x0CFFFFFF)
                )
            )
        )
    }

    val solidGlassBackground = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF0101520),
                Color(0xFA070A10)
            )
        )
    }

    val shader = if (enableAgsl) GlassShaderCache.runtimeShader else null

    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this
            .shadow(elevation, shape)
            .clip(shape)
            .graphicsLayer {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("cornerRadius", cornerRadius.toPx())
                shader.setFloatUniform("refractionStrength", 0.04f)
                shader.setFloatUniform("glareIntensity", 0.5f)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
            }
            .border(solidGlassBorder, shape)
    } else {
        // High-Performance Solid Glass Black hardware background
        this
            .shadow(elevation, shape)
            .clip(shape)
            .background(solidGlassBackground)
            .border(solidGlassBorder, shape)
    }
}
