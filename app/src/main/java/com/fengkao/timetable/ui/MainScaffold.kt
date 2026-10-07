package com.fengkao.timetable.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Shower
import com.fengkao.timetable.ui.water.WaterScreen
import com.fengkao.timetable.ui.shower.ShowerScreen
import com.fengkao.timetable.ui.water.WaterViewModel
import com.fengkao.timetable.ui.shower.ShowerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.ui.glass.DampedDragAnimation
import com.fengkao.timetable.ui.glass.GlassElevation
import com.fengkao.timetable.ui.glass.liquidGlassSurface
import com.fengkao.timetable.ui.glass.InteractiveHighlight
import com.fengkao.timetable.ui.profile.ProfileScreen
import com.fengkao.timetable.ui.navigation.LocalPredictiveBack
import com.fengkao.timetable.ui.navigation.LocalPageActive
import com.fengkao.timetable.ui.schedule.ScheduleScreen
import com.fengkao.timetable.ui.theme.BarCenterVeil
import com.fengkao.timetable.ui.theme.BarScrim
import com.fengkao.timetable.ui.theme.BarTopGlint
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.SageBg
import com.fengkao.timetable.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val TabInk: Color
    get() = Color(0xFF9AA3B2)

/** 课表滚动联动：滚动时底栏遮罩加深（ScheduleScreen 写入，底栏读取） */
object GlassBarState {
    var scrimBoost by mutableStateOf(0f)
}
private val BarHeight = 64.dp
private val PillHeight = 56.dp
private const val TabsCount = 4

private val TabDefs = listOf(
    Triple(0, Icons.Outlined.CalendarMonth, "课程"),
    Triple(1, Icons.Outlined.WaterDrop, "喝水"),
    Triple(2, Icons.Outlined.Shower, "洗澡"),
    Triple(3, Icons.Outlined.Person, "我的"),
)

/** 选项内容的按压缩放系数（拖动药丸时图标随之放大） */
val LocalTabPressScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/** 主框架：内容 + 悬浮液态玻璃底栏（backdrop 实时折射课程内容） */
@Composable
fun MainScaffold(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waterModel: WaterViewModel = viewModel()
    val showerModel: ShowerViewModel = viewModel()
    val tabStateHolder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .background(appBackground())
    ) {
        // 内容注册为玻璃折射源：底栏实时折射滚动的课程卡片
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "tab",
            ) { tab ->
                tabStateHolder.SaveableStateProvider(tab) {
                    CompositionLocalProvider(LocalPredictiveBack provides state.settings.predictiveBack, LocalPageActive provides (tab == state.selectedTab)) {
                    when (tab) {
                        1 -> WaterScreen(waterModel)
                        2 -> ShowerScreen(showerModel)
                        3 -> ProfileScreen(viewModel, waterModel, showerModel)
                        else -> ScheduleScreen(viewModel)
                    }
                    }
                }
            }
        }

        GlassBottomBar(
            selected = state.selectedTab,
            onTabSelected = viewModel::setTab,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
    }
}

/**
 * 液态玻璃底栏（AndroidLiquidGlass 官方 LiquidBottomTabs 交互）：
 * - 药丸可随手指在选项间无极拖动，松手回弹到最近档位，边缘经过时 lens 色散折射
 * - 手指按下时整体微微放大，药丸带阻尼压缩 + 速度挤压形变
 * - 高光跟随手指位置（AGSL 镜面反射）
 */
@Composable
private fun GlassBottomBar(
    selected: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tabsBackdrop = rememberLayerBackdrop()
    val barBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / TabsCount
        }

        // 边缘挤压位移：药丸拖到边缘时整体内容被轻微“推挤”
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(TabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 61.5f / 56f, // 按压放大约 1.1 倍（用户要求再降 50%）
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, TabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                    onTabSelected(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (TabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }
        // 外部（点击 tab）驱动的选择同步：首次直接落位，之后带弹性动画
        var firstSync by remember { mutableStateOf(true) }
        LaunchedEffect(selected) {
            if (firstSync) {
                firstSync = false
                dampedDragAnimation.updateValue(selected.toFloat())
            } else {
                dampedDragAnimation.animateToValue(selected.toFloat())
            }
        }
        // 镜面高光：高光中心始终跟随药丸当前位置
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(animationScope = animationScope, position = { size, offset ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            })
        }
        // 拖动经过时按压缩放系数提供给 tab 内容
        val tabPressScale = { lerp(1f, 1.1f, dampedDragAnimation.pressProgress) }
        // 手指按在底栏任意位置时整体微微放大
        val barPress = remember { Animatable(0f) }

        CompositionLocalProvider(LocalTabPressScale provides tabPressScale) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .graphicsLayer {
                    val s = 1f + 0.015f * barPress.value
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(animationScope) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        animationScope.launch { barPress.animateTo(1f, spring(1f, 500f, 0.001f)) }
                        waitForUpOrCancellation(PointerEventPass.Initial)
                        animationScope.launch { barPress.animateTo(0f, spring(0.8f, 320f, 0.001f)) }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
                // 1) 隐藏的品牌色内容层：被药丸折射后，选中项呈现品牌色
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                            colorFilter = ColorFilter.tint(Color(0xFF4E6EF2))
                        }
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .height(PillHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabDefs.forEach { (i, icon, label) ->
                        TabContent(i, icon, label, Modifier.weight(1f))
                    }
                }

                // 2) 玻璃底板：统一 LiquidGlassSurface（Chrome 层）；onSurface 三层动态遮罩
                Row(
                    Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .layerBackdrop(barBackdrop)
                        .liquidGlassSurface(
                            backdrop = backdrop,
                            elevation = GlassElevation.Chrome,
                            lensDp = 26.dp,
                            chromatic = true,
                            pressProgress = { dampedDragAnimation.pressProgress },
                            onSurface = {
                                // ① 滚动联动基础遮罩
                                drawRect(
                                    BarScrim.copy(
                                        alpha = BarScrim.alpha + 0.18f * GlassBarState.scrimBoost
                                    )
                                )
                                // ② 分区渲染：中心 80% 内容区垫层，边缘 10% 通透
                                drawRect(
                                    Brush.horizontalGradient(
                                        listOf(
                                            BarCenterVeil.copy(alpha = 0f),
                                            BarCenterVeil,
                                            BarCenterVeil,
                                            BarCenterVeil.copy(alpha = 0f),
                                        ),
                                        startX = 0f,
                                        endX = size.width,
                                    )
                                )
                                // ③ 顶缘 1~2dp 高光条
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(BarTopGlint, Color.Transparent),
                                        startY = 0f,
                                        endY = 2f.dp.toPx(),
                                    )
                                )
                            },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(BarHeight)
                        .fillMaxWidth(),
                ) {}

                // 3) 可见 tab 文字（可点击），绘制在玻璃板之上
                Row(
                    Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .height(BarHeight)
                        .fillMaxWidth()
                        .padding(4f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabDefs.forEach { (i, icon, label) ->
                        SelectableTab(i, icon, label, onTabSelected, Modifier.weight(1f))
                    }
                }

                // 3) 可拖动放大镜药丸：lens 色散折射 + 按压压缩 + 速度形变
                Box(
                    Modifier
                        .padding(horizontal = 4f.dp)
                        .graphicsLayer {
                            translationX =
                                if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                                else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                        }
                        .then(interactiveHighlight.gestureModifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            // 场景 + 底栏自身 + 品牌色内容层：透过药丸能看到底栏边缘被折射"缩小"
                            backdrop = rememberCombinedBackdrop(backdrop, barBackdrop, tabsBackdrop),
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    10f.dp.toPx() * progress,
                                    14f.dp.toPx() * progress,
                                    chromaticAberration = true
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            shadow = {
                                val progress = dampedDragAnimation.pressProgress
                                Shadow(alpha = progress)
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = 8f.dp * progress,
                                    alpha = progress
                                )
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                // 底栏自身的奶白层已在折射背景中，这里只补极薄的一层
                                drawRect(
                                    Color.White.copy(alpha = 0.05f),
                                    alpha = 1f - progress
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .height(PillHeight)
                        .fillMaxWidth(1f / TabsCount)
                )
            }
        }
    }
}

/** 底栏 tab 内容（隐藏层与可见层共用，保证折射对位） */
@Composable
private fun TabContent(
    index: Int,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val pressScale = LocalTabPressScale.current
    Row(
        modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.graphicsLayer {
                val s = pressScale()
                scaleX = s
                scaleY = s
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = TabInk,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = TabInk,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SelectableTab(
    index: Int,
    icon: ImageVector,
    label: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    TabContent(
        index,
        icon,
        label,
        modifier.clickable(interactionSource = interaction, indication = null) { onSelected(index) },
    )
}
