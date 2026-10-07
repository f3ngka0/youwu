package com.fengkao.timetable.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.BrandGreenSoft
import com.fengkao.timetable.ui.theme.CardWhite
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(com.fengkao.timetable.ui.theme.SageBg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
        ) {
            Spacer(Modifier.height(64.dp))

            // 品牌区
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(BrandGreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("有梧", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Text("登录教务系统，自动同步你的课表", fontSize = 13.sp, color = InkMid)
            Spacer(Modifier.height(26.dp))

            TeachingLoginForm(viewModel)

            Spacer(Modifier.height(18.dp))
            Text(
                "账号密码仅保存在本机（系统级加密存储），\n课表数据直连学校教务系统获取",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = InkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.reopenWelcome() }, enabled = !state.loginBusy,
                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("欢迎与初次设置", fontSize = 12.sp, color = InkMid)
            }
        }
    }
}

@Composable
internal fun TeachingLoginForm(viewModel: MainViewModel, submitLabel: String = "登录") {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var account by rememberSaveable { mutableStateOf(state.settings.account) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(true) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var baseUrl by rememberSaveable { mutableStateOf(state.settings.baseUrl) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardWhite)
            .padding(20.dp)
    ) {
        FieldLabel("学号")
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            placeholder = { Text("请输入学号", color = InkGray) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            colors = focusColors(),
        )

        Spacer(Modifier.height(14.dp))
        FieldLabel("密码")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("请输入密码", color = InkGray) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Icon(
                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = "显示/隐藏密码",
                    tint = InkGray,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { showPassword = !showPassword },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = focusColors(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { remember = !remember }
                .padding(top = 4.dp),
        ) {
            Checkbox(
                checked = remember,
                onCheckedChange = { remember = it },
                colors = CheckboxDefaults.colors(checkedColor = BrandGreen),
            )
            Text("记住密码并自动更新课表", fontSize = 13.sp, color = InkMid)
        }

        TextButton(onClick = { advanced = !advanced }) {
            Text(
                if (advanced) "收起服务器设置" else "服务器设置",
                fontSize = 12.sp,
                color = InkGray,
            )
        }
        AnimatedVisibility(visible = advanced) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("教务系统地址", fontSize = 12.sp, color = InkMid) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = focusColors(),
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                viewModel.login(account.trim(), password, baseUrl.trim(), remember)
            },
            enabled = !state.loginBusy,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.loginBusy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(submitLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        state.loginError?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = InkMid,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun focusColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandGreen,
    cursorColor = BrandGreen,
    unfocusedContainerColor = CardWhite,
    focusedContainerColor = CardWhite,
)
