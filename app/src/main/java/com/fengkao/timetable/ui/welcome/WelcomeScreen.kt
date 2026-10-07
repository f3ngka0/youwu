package com.fengkao.timetable.ui.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fengkao.timetable.SyncScheduler
import com.fengkao.timetable.R
import kotlinx.coroutines.delay
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.ui.MainUiState
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.login.TeachingLoginForm
import com.fengkao.timetable.ui.navigation.LocalPredictiveBack
import com.fengkao.timetable.ui.navigation.PageStack
import com.fengkao.timetable.ui.navigation.PageMotion
import com.fengkao.timetable.ui.profile.*
import com.fengkao.timetable.ui.shower.ShowerLogin
import com.fengkao.timetable.ui.shower.ShowerViewModel
import com.fengkao.timetable.ui.theme.*
import com.fengkao.timetable.ui.water.LifeCard
import com.fengkao.timetable.ui.water.LifeTheme
import com.fengkao.timetable.ui.water.ServiceMessage
import com.fengkao.timetable.ui.water.WaterLogin
import com.fengkao.timetable.ui.water.WaterViewModel

private enum class WelcomeStep(val label: String, val title: String, val subtitle: String) {
    INTRO("欢迎", "欢迎使用有梧", "校园大小事，有梧搞定"),
    TEACHING("教务", "连接教务账户", "使用学校教务系统的学号和密码，自动同步课程。"),
    SERVICES("服务", "连接校园服务", "喝水与洗澡账户分别管理，选择你需要的服务。"),
    PREFERENCES("偏好", "按你的习惯看课表", "调整常用设置，之后也能在“我的”里修改。"),
    READY("完成", "设置好了", "你的校园日常，准备就绪。"),
}

@Composable
fun WelcomeScreen(mainModel: MainViewModel) {
    val main by mainModel.state.collectAsStateWithLifecycle()
    // Same activity-scoped models as the bottom navigation and account settings.
    val waterModel: WaterViewModel = viewModel()
    val showerModel: ShowerViewModel = viewModel()
    val water by waterModel.state.collectAsStateWithLifecycle()
    val shower by showerModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(main.settings.welcomeStep) }
    var service by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = main.loginBusy || main.firstFetchActive || main.welcomeSaving || water.busy || shower.busy

    fun go(next: Int) {
        keyboard?.hide()
        service = null
        step = next.coerceIn(0, WelcomeStep.entries.lastIndex)
        mainModel.setWelcomeStep(step)
    }
    fun back() {
        keyboard?.hide()
        if (!main.loginBusy && !main.firstFetchActive && !main.welcomeSaving) {
            if (service != null) service = null else go(step - 1)
        }
    }
    LaunchedEffect(water.loggedIn, shower.loggedIn) {
        if ((service == "water" && water.loggedIn) || (service == "shower" && shower.loggedIn)) service = null
    }

    val pages = WelcomeStep.entries.take(step + 1).map { it.name } + listOfNotNull(service)
    LifeTheme {
        CompositionLocalProvider(LocalPredictiveBack provides main.settings.predictiveBack) {
            val isService = service != null
            val current = WelcomeStep.entries[step]
            val canContinue = !busy && (current != WelcomeStep.TEACHING ||
                (main.loggedIn && !main.firstFetchFailed))
            WelcomeLayout(
                step = current,
                onBack = if (current == WelcomeStep.INTRO) null else ::back,
                backEnabled = !main.loginBusy && !main.firstFetchActive && !main.welcomeSaving,
                nextLabel = when {
                    isService -> "返回账户列表"
                    current == WelcomeStep.INTRO -> "开始设置"
                    current == WelcomeStep.READY -> if (main.loggedIn) "开始使用" else "完成并去登录"
                    else -> "下一步"
                },
                nextEnabled = if (isService) !busy else canContinue,
                saving = main.welcomeSaving,
                onNext = {
                    if (isService) { keyboard?.hide(); service = null }
                    else if (current == WelcomeStep.READY) mainModel.finishWelcome()
                    else go(current.ordinal + 1)
                },
                skip = if (current == WelcomeStep.TEACHING && !main.loggedIn && !busy)
                    ({ go(current.ordinal + 1) }) else null,
                error = main.welcomeError,
                servicePage = isService,
            ) {
                PageStack(pages, ::back, motion = PageMotion.WELCOME) { page ->
                    holder.SaveableStateProvider(page) {
                        val current = if (page == "water" || page == "shower") WelcomeStep.SERVICES else WelcomeStep.valueOf(page)
                        WelcomeBody(step = current,
                            title = when (page) { "water" -> "登录喝水账户"; "shower" -> "登录洗澡账户"; else -> current.title },
                            subtitle = when (page) {
                                "water" -> "使用慧生活798账户，登录后可绑定饮水设备。"
                                "shower" -> "使用趣智校园账户，登录后可连接浴室热水器。"
                                else -> if (current == WelcomeStep.READY && !main.loggedIn)
                                    "偏好已保存，接下来登录教务账户即可查看课程。" else current.subtitle
                            },
                            isService = page == "water" || page == "shower",
                        ) {
                            when (page) {
                                "water" -> {
                                    WaterLogin(water, waterModel, showHeading = false, submitLabel = "登录喝水账户")
                                    ServiceMessage(water.message, water.error)
                                }
                                "shower" -> {
                                    ShowerLogin(shower, showerModel, showHeading = false, submitLabel = "登录洗澡账户")
                                    ServiceMessage(shower.message, shower.error)
                                }
                                else -> when (current) {
                                    WelcomeStep.INTRO -> WelcomeIntro()
                                    WelcomeStep.TEACHING -> TeachingSetup(main, mainModel)
                                    WelcomeStep.SERVICES -> {
                                        SettingsSection {
                                            SetupAccountItem("喝水账户", Icons.Outlined.WaterDrop,
                                                if (water.loggedIn) maskAccount(water.phone) else "慧生活798 · 连接饮水设备",
                                                water.loggedIn, water.busy) { service = "water" }
                                            SettingsDivider()
                                            SetupAccountItem("洗澡账户", Icons.Outlined.Shower,
                                                if (shower.loggedIn) maskAccount(shower.phone) else "趣智校园 · 蓝牙开关水",
                                                shower.loggedIn, shower.busy) { service = "shower" }
                                        }
                                    }
                                    WelcomeStep.PREFERENCES -> {
                                        SettingsSection {
                                            SettingsSwitchItem("显示周末", Icons.Outlined.DateRange,
                                                "显示周六、周日", main.settings.showWeekend,
                                                mainModel::setShowWeekend, divider = true, subtitleColor = InkMid)
                                            SettingsSwitchItem("自动更新课程表", Icons.Outlined.Sync,
                                                "启动时同步，每 ${SyncScheduler.PERIOD_HOURS} 小时后台更新",
                                                main.settings.autoSync, mainModel::setAutoSync, subtitleColor = InkMid)
                                        }
                                        SettingsSection("学期与作息") {
                                            val term = main.term ?: TermCalculator.autoTerm()
                                            SettingsItem("第一周第一天", Icons.Outlined.Event,
                                                value = mainModel.termStartOf(main.settings, term).toString(), divider = true, valueColor = InkMid) { editor = "date" }
                                            SettingsItem("学期周数", Icons.Outlined.CalendarMonth,
                                                value = "${main.settings.termWeeks} 周", divider = true, valueColor = InkMid) { editor = "weeks" }
                                            SettingsItem("上课时间", Icons.Outlined.Schedule,
                                                "查看并调整每一节课的时间") { editor = "periods" }
                                        }
                                        if (main.settings.passwordEnc.isBlank())
                                            SettingsHint("登录教务账户时勾选“记住密码”，即可使用自动更新。")
                                    }
                                    WelcomeStep.READY -> {
                                        ReadyMark()
                                        SettingsSection("账户连接") {
                                            SetupStatus("教务账户", if (main.loggedIn) "已保存" else "稍后登录", main.loggedIn)
                                            SetupStatus("喝水账户", if (water.loggedIn) "已登录" else "稍后登录", water.loggedIn)
                                            SetupStatus("洗澡账户", if (shower.loggedIn) "已登录" else "稍后登录", shower.loggedIn)
                                        }
                                        SettingsSection("课表偏好") {
                                            SettingsValue("显示周末", if (main.settings.showWeekend) "开启" else "关闭", InkMid)
                                            SettingsValue("自动更新", if (main.settings.autoSync) "开启" else "关闭", InkMid)
                                            SettingsValue("学期周数", "${main.settings.termWeeks} 周", InkMid)
                                        }
                                        SettingsHint("喝水和洗澡设备可在对应页面的“设备管理”中绑定。")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ProfileEditors(editor, main.settings,
            mainModel.termStartOf(main.settings, main.term ?: TermCalculator.autoTerm()), mainModel) { editor = null }
    }
}

@Composable
private fun WelcomeLayout(
    step: WelcomeStep, onBack: (() -> Unit)?, backEnabled: Boolean,
    nextLabel: String, nextEnabled: Boolean, saving: Boolean, onNext: () -> Unit,
    skip: (() -> Unit)?, error: String?, servicePage: Boolean, content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(if (pressed) 80 else 160), label = "welcome-button")
    Column(Modifier.fillMaxSize().background(appBackground())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上一步", tint = InkDark)
                }
            }
            Text("初次设置", color = InkMid, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${step.ordinal + 1} / ${WelcomeStep.entries.size}", color = InkMid, fontSize = 12.sp)
        }
        WelcomeProgressBar(step)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp)) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onNext, enabled = nextEnabled, interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    AnimatedContent(nextLabel, transitionSpec = {
                        fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                    }, label = "welcome-action-label") { label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                if (skip != null) TextButton(onClick = skip) { Text("暂时跳过", color = InkMid) }
                else {
                    val hint = when {
                        servicePage -> "可以稍后登录"
                        step == WelcomeStep.SERVICES -> "可跳过，之后也能添加"
                        step == WelcomeStep.READY -> "之后可在“我的”中修改设置"
                        else -> null
                    }
                    if (hint != null) Text(hint, color = InkMid, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomeProgressBar(step: WelcomeStep) {
    val position by animateFloatAsState(step.ordinal.toFloat(), tween(360, easing = FastOutSlowInEasing), label = "welcome-step-marker")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 24.dp) / WelcomeStep.entries.size
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WelcomeStep.entries.forEach { _ ->
                    Box(Modifier.width(width).height(3.dp).clip(CircleShape).background(Hairline))
                }
            }
            Box(Modifier.offset(x = (width + 6.dp) * position).width(width).height(3.dp)
                .clip(CircleShape).background(BrandGreen))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WelcomeStep.entries.forEach { item -> Text(item.label, Modifier.weight(1f),
                color = if (item == step) BrandGreen else InkMid, textAlign = TextAlign.Center,
                fontSize = 12.sp, fontWeight = if (item == step) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun WelcomeBody(step: WelcomeStep, title: String, subtitle: String, isService: Boolean,
    content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centered = !isService && step == WelcomeStep.INTRO
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
            .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, if (centered) Alignment.CenterVertically else Alignment.Top)) {
            if (step != WelcomeStep.INTRO) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, color = InkDark, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    if (step == WelcomeStep.SERVICES && !isService) Text("可选", color = BrandGreen, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BrandGreenSoft).padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Text(subtitle, color = InkMid, fontSize = 14.sp, lineHeight = 22.sp)
            }
            content()
        }
    }
}

@Composable
private fun WelcomeIntro() {
    IntroReveal(0, scale = true) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_app_logo), "有梧 App 图标", modifier = Modifier.size(128.dp))
        }
    }
    IntroReveal(70) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(WelcomeStep.INTRO.title, color = InkDark, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(WelcomeStep.INTRO.subtitle, color = InkMid, fontSize = 14.sp, lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
    IntroReveal(140) {
        SettingsSection {
            WelcomeFeature("课程", "教务同步与课表", Icons.Outlined.CalendarMonth)
            WelcomeFeature("喝水", "绑定常用饮水设备", Icons.Outlined.WaterDrop)
            WelcomeFeature("洗澡", "蓝牙连接浴室热水器", Icons.Outlined.Shower)
        }
    }
}

@Composable
private fun IntroReveal(delayMillis: Long, scale: Boolean = false, content: @Composable () -> Unit) {
    var appeared by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { Animatable(if (appeared) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!appeared) {
            appeared = true
            delay(delayMillis)
            reveal.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = reveal.value
        translationY = (1f - reveal.value) * 16.dp.toPx()
        scaleX = if (scale) .96f + .04f * reveal.value else 1f
        scaleY = scaleX
    }) { content() }
}

@Composable
private fun WelcomeFeature(title: String, detail: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = BrandGreen, modifier = Modifier.size(22.dp))
        Text(title, color = InkDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = InkMid, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TeachingSetup(state: MainUiState, model: MainViewModel) {
    when {
        state.firstFetchActive -> LifeCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), color = BrandGreen, strokeWidth = 2.dp)
                Text("正在登录并同步课程", color = InkDark, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Text(state.fetchLogs.lastOrNull() ?: "正在连接教务系统…", color = InkMid, fontSize = 13.sp, lineHeight = 20.sp)
        }
        state.loggedIn && !state.firstFetchFailed -> {
            LifeCard {
                Icon(Icons.Outlined.CheckCircle, null, tint = BrandGreen, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (state.schedule != null) "教务账户已连接" else "教务账户已保存",
                    color = InkDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(state.schedule?.student?.name?.takeIf { it.isNotBlank() } ?: maskAccount(state.settings.account),
                    color = InkMid, modifier = Modifier.padding(top = 6.dp))
                Text(if (state.schedule != null) "课表已同步，点击下一步继续设置。" else "稍后可在“我的 → 同步与数据”中获取课程。",
                    color = InkGray, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        else -> {
            if (state.firstFetchFailed) LifeCard {
                Text("课表暂未获取成功", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                Text(state.fetchLogs.lastOrNull() ?: "请检查网络和教务账户后重试", color = InkMid, fontSize = 12.sp,
                    lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(onClick = model::retryFirstFetch) { Text("重试同步") }
                    TextButton(onClick = model::skipFirstFetch) { Text("稍后同步") }
                }
            }
            TeachingLoginForm(model, if (state.firstFetchFailed) "重新登录并同步" else "登录并同步课表")
            SettingsHint("账户信息加密保存在本机，课表直连学校教务系统。")
        }
    }
}

@Composable
private fun SetupAccountItem(title: String, icon: ImageVector, subtitle: String,
    connected: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !connected && !busy, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = BrandGreen, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = InkDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = InkMid, fontSize = 13.sp, lineHeight = 20.sp)
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else if (connected) Icon(Icons.Outlined.CheckCircle, "已登录", tint = BrandGreen, modifier = Modifier.size(22.dp))
        else Text("登录", color = BrandGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReadyMark() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_app_logo), null, modifier = Modifier.size(64.dp))
            Icon(Icons.Outlined.CheckCircle, "设置完成", tint = BrandGreen,
                modifier = Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(CardWhite))
        }
    }
}

@Composable
private fun SetupStatus(title: String, status: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = InkDark, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(status, color = if (connected) BrandGreen else InkMid, fontSize = 13.sp)
    }
}
