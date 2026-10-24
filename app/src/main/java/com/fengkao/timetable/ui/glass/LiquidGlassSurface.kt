package com.fengkao.timetable.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.fengkao.timetable.ui.theme.GlassControlColor
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.GlassElevatedColor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 统一的 Liquid Glass 材质层级（DESIGN-liquid-glass.md 分层规范）。
 * 全部控件（分段控制器 / 圆形按钮 / 底栏）复用同一套参数，保证材质一致。
 */
enum class GlassElevation(
    val blurDp: Float,
    val lensDp: Float,
    val highlightAlpha: Float,
    val shadowAlpha: Float,
    val pressScaleDp: Float,
) {
    /** 底栏等贴画布浮层：最薄，折射最强 */
    Chrome(blurDp = 8f, lensDp = 24f, highlightAlpha = 0.9f, shadowAlpha = 0.10f, pressScaleDp = 8f),

    /** 控件：分段控制器、悬浮按钮、chip */
    Control(blurDp = 10f, lensDp = 10f, highlightAlpha = 0.8f, shadowAlpha = 0.08f, pressScaleDp = 6f),

    /** 厚玻璃浮层 */
    Elevated(blurDp = 16f, lensDp = 12f, highlightAlpha = 0.85f, shadowAlpha = 0.16f, pressScaleDp = 6f),
}

/**
 * 统一 Liquid Glass 材质：
 * 半透明 + backdrop blur + vibrancy 饱和透射 + SDF 折射 + 高光/阴影 + 细边 + 可选按压缩放。
 *
 * @param pressProgress 可选按压进度（0..1），驱动底板轻微缩放
 * @param onSurface 额外的表面绘制（如底栏的动态遮罩），在默认垫层之后执行
 */
@Composable
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    elevation: GlassElevation = GlassElevation.Control,
    lensDp: Dp = elevation.lensDp.dp,
    chromatic: Boolean = false,
    corner: Dp? = null,
    pressProgress: (() -> Float)? = null,
    // 投影强度覆盖：落在画布中间的玻璃卡不需要外投影（浅灰画布上会读成一团灰晕），传 0f 关掉
    shadowAlpha: Float = elevation.shadowAlpha,
    // 内置受光面强度覆盖：深色底上 Highlight.Default 会把整圈边缘刷成亮白，
    // 卡片一大就变成夸张的霓虹描边，需要单独压低。
    highlightAlpha: Float = elevation.highlightAlpha,
    // 最外层 1dp 描边色：默认 GlassEdge。深色画布上的大卡片要自己画一道更淡的边，
    // 传 Transparent 关掉这里的内置描边，避免叠出两圈。
    edgeColor: Color = GlassEdge,
    onSurface: (DrawScope.() -> Unit)? = null,
): Modifier {
    val blurPx = with(LocalDensity.current) { elevation.blurDp.dp.toPx() }
    val lensPx = with(LocalDensity.current) { lensDp.toPx() }
    val pressPx = with(LocalDensity.current) { elevation.pressScaleDp.dp.toPx() }
    val shape = corner?.let { RoundedCornerShape(it) }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape ?: Capsule() },
            effects = {
                vibrancy()
                blur(blurPx)
                lens(lensPx, lensPx, chromaticAberration = chromatic)
            },
            highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
            shadow = { Shadow(alpha = shadowAlpha) },
            layerBlock = {
                pressProgress?.let { pp ->
                    val progress = pp()
                    val scale = lerp(1f, 1f + pressPx / size.width, progress)
                    scaleX = scale
                    scaleY = scale
                }
            },
            onDrawSurface = {
                when (elevation) {
                    GlassElevation.Control -> drawRect(GlassControlColor)
                    GlassElevation.Elevated -> drawRect(GlassElevatedColor)
                    GlassElevation.Chrome -> {}
                }
                onSurface?.invoke(this)
            },
        )
        // 极细高光边，克制的白度（浅色 55% / 深色 18%）
        .border(1.dp, edgeColor, shape ?: RoundedCornerShape(50))
}
