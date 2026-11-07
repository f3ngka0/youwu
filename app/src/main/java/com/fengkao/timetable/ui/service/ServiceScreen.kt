package com.fengkao.timetable.ui.service

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.ui.glass.GlassElevation
import com.fengkao.timetable.ui.glass.liquidGlassSurface
import com.fengkao.timetable.ui.shower.ShowerScreen
import com.fengkao.timetable.ui.shower.ShowerViewModel
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.appBackground
import com.fengkao.timetable.ui.water.ServiceKind
import com.fengkao.timetable.ui.water.WaterScreen
import com.fengkao.timetable.ui.water.WaterViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/** 服务页选中的服务，切换 Tab 后保留。 */
object ServiceSelection {
    var index by mutableIntStateOf(0)
}

/**
 * 校园服务：喝水与洗澡合到一页，用底栏同款液态玻璃分段栏切换。
 * 两个服务各自保留原有的滚动容器与页面栈，这里只负责抬头与切换。
 */
@Composable
fun ServiceScreen(waterModel: WaterViewModel, showerModel: ShowerViewModel) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // 折射源只包含页面底色：分段栏用它做玻璃垫层，避免和自身内容互相采样。
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(appBackground()))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "校园服务",
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = InkDark,
                letterSpacing = 0.3.sp,
            )
            ServiceSwitcher(
                selected = ServiceSelection.index,
                onSelect = { ServiceSelection.index = it },
                backdrop = backdrop,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 10.dp),
            )
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = ServiceSelection.index,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                    label = "service",
                ) { index ->
                    if (index == 0) WaterScreen(waterModel, embedded = true)
                    else ShowerScreen(showerModel, embedded = true)
                }
            }
        }
    }
}

@Composable
private fun ServiceSwitcher(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(ServiceKind.Drinking, Icons.Outlined.WaterDrop, "喝水"),
        Triple(ServiceKind.Shower, Icons.Outlined.Shower, "洗澡"),
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp),
    ) {
        val density = LocalDensity.current
        val lensWidth = with(density) { (constraints.maxWidth - 6.dp.toPx()) / 2f }
        val position = remember { Animatable(selected.toFloat()) }
        LaunchedEffect(selected) {
            position.animateTo(selected.toFloat(), spring(dampingRatio = 0.86f, stiffness = 520f))
        }
        // 玻璃底槽：与底栏同一套材质层级，内衬一层浅灰让槽体在浅色画布上读得出来
        Box(
            Modifier.fillMaxSize().liquidGlassSurface(
                backdrop = backdrop,
                elevation = GlassElevation.Control,
                corner = 22.dp,
                shadowAlpha = 0.10f,
                onSurface = {
                    drawRect(if (IsDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.035f))
                },
            ),
        ) {}
        // 选中药丸：白玻璃滑块，位置随选择滑动
        Box(
            Modifier
                .padding(3.dp)
                .offset { IntOffset((lensWidth * position.value).roundToInt(), 0) }
                .width(with(density) { lensWidth.toDp() })
                .fillMaxHeight()
                .shadow(6.dp, RoundedCornerShape(19.dp))
                .clip(RoundedCornerShape(19.dp))
                .background(if (IsDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.92f)),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, (kind, icon, label) ->
                val active = index == selected
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 分段栏自己给滑动反馈，不要系统水波纹
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = if (active) kind.ink else InkGray)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) InkDark else InkMid,
                    )
                }
            }
        }
    }
}
