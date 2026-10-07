package com.fengkao.timetable.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.fengkao.timetable.ui.theme.appBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

val LocalPredictiveBack = staticCompositionLocalOf { false }
val LocalPageActive = staticCompositionLocalOf { true }

enum class PageMotion { STANDARD, WELCOME }

/** 普通返回横向退场；启用预测性返回时预览真实父页面。 */
@Composable
fun PageStack(stack: List<String>, onBack: () -> Unit, motion: PageMotion = PageMotion.STANDARD,
    content: @Composable (String) -> Unit) {
    val enabled = LocalPageActive.current && stack.size > 1
    val predictive = LocalPredictiveBack.current
    val transition = updateTransition(stack, label = "page-stack")
    val progress = remember { Animatable(0f) }
    var preview by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var fromRight by remember { mutableStateOf(false) }
    LaunchedEffect(stack) { if (committed) { delay(320); committed = false } }
    BackHandler(enabled = enabled && (!predictive || transition.isRunning), onBack = onBack)
    PredictiveBackHandler(enabled = enabled && predictive && !transition.isRunning) { events ->
        try {
            events.collect { event ->
                fromRight = event.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT
                preview = true
                progress.snapTo(event.progress)
            }
            if (preview) progress.animateTo(1f, tween(140))
            committed = preview
            onBack()
        } catch (_: CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { progress.animateTo(0f, tween(160)) }
        } finally {
            preview = false
            progress.snapTo(0f)
        }
    }
    Box(Modifier.fillMaxSize().background(appBackground())) {
        if (preview && stack.size > 1) Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = .55f + progress.value * .45f
            scaleX = .97f + progress.value * .03f; scaleY = scaleX
        }) { content(stack[stack.lastIndex - 1]) }
        transition.AnimatedContent(contentKey = { it.last() }, modifier = Modifier.fillMaxSize().graphicsLayer {
            if (preview) {
                val p = progress.value
                translationX = size.width * p * if (fromRight) -1f else 1f
                scaleX = 1f - .04f * p; scaleY = scaleX
            }
        }, transitionSpec = {
            val duration = if (motion == PageMotion.WELCOME) 360 else 280
            val enterDistance = if (motion == PageMotion.WELCOME) 4 else 5
            val exitDistance = if (motion == PageMotion.WELCOME) 8 else 10
            val enterFade = if (motion == PageMotion.WELCOME) 220 else 200
            val exitFade = if (motion == PageMotion.WELCOME) 140 else 160
            if (committed) EnterTransition.None togetherWith ExitTransition.None
            else if (targetState.size > initialState.size)
                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it / enterDistance } + fadeIn(tween(enterFade))) togetherWith
                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it / exitDistance } + fadeOut(tween(exitFade)))
            else (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it / exitDistance } + fadeIn(tween(enterFade))) togetherWith
                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it / enterDistance } + fadeOut(tween(exitFade)))
        }) { pages -> content(pages.last()) }
    }
}
