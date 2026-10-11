package com.fengkao.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.ui.MainScaffold
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.login.FetchProgressScreen
import com.fengkao.timetable.ui.login.LoginScreen
import com.fengkao.timetable.ui.theme.SageBg
import com.fengkao.timetable.ui.theme.TimetableTheme
import com.fengkao.timetable.ui.welcome.WelcomeScreen
import com.fengkao.timetable.widget.WidgetClicks
import com.fengkao.timetable.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        setContent {
            TimetableTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 打开 App 即回写桌面 Widget（数据可能因同步/设置变化而过期）
        WidgetUpdater.requestUpdateAll(this)
        com.fengkao.timetable.widget.ServiceWidgetUpdater.updateAll(this)
    }

    /** 桌面 Widget 深链：点击课程 → 打开该课程详情 */
    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == WidgetClicks.ACTION_OPEN_COURSE) {
            val key = intent.getStringExtra(WidgetClicks.EXTRA_COURSE_KEY)
            if (!key.isNullOrBlank()) viewModel.openCourseFromWidget(key)
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        !state.bootstrapped -> Box(
            Modifier.fillMaxSize().background(SageBg),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = com.fengkao.timetable.ui.theme.BrandGreen) }

        !state.settings.welcomeCompleted -> WelcomeScreen(viewModel)

        !state.loggedIn -> LoginScreen(viewModel)

        // 登录后的首次课表获取：进度页（含日志），获取完成才进入主界面
        state.firstFetchActive || state.firstFetchFailed -> FetchProgressScreen(
            logs = state.fetchLogs,
            failed = state.firstFetchFailed,
            onRetry = viewModel::retryFirstFetch,
            onSkip = viewModel::skipFirstFetch,
        )

        else -> MainScaffold(viewModel)
    }
}
