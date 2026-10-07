# 构建有梧

面向需要自行构建的开发者。使用说明与功能截图见 [README.md](README.md)。

## 环境

- JDK 17
- Android SDK Platform 36、Build-Tools 36.0.0、Platform-Tools
- Python 3.13（构建机）
- Gradle 8.14.3，使用仓库内的 wrapper

在 Android Studio 中打开项目，或设置 JAVA_HOME 并创建本机 local.properties，将 sdk.dir 指向 Android SDK。SDK、JDK 的路径由你的安装位置决定，不需要开发者原来的目录。

## 构建

~~~powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
~~~

Debug 产物位于 app/build/outputs/apk/debug/。Release 位于 app/build/outputs/apk/release/，默认只包含 arm64-v8a。内嵌的 school-sdk MIT wheel 位于 vendor/，不需要额外下载本机私有 SDK 文件。

## 服务配置和签名

将 water.properties.example 复制为 water.properties，自行填写合法使用的饮水平台配置。缺失配置时仍可构建，喝水页提示未配置。真实文件不进入版本管理。

将 keystore.properties.example 复制为 keystore.properties，填写自己的签名密钥路径和凭据。没有签名密钥时生成未签名 Release 包；发布可安装包需要正式签名。密钥库、密码和本机配置不得提交。

## 离线演示与截图

~~~powershell
.\gradlew.bat assembleDemo
adb install -r app/build/outputs/apk/demo/app-demo.apk
adb shell am start -n com.fengkao.timetable.demo/com.fengkao.timetable.demo.DemoActivity --es screen profile
~~~

演示包拥有独立 applicationId（com.fengkao.timetable.demo），使用生产界面与虚构数据，移除了联网和蓝牙权限。它不会读取正式应用的私有目录。screen 可选 welcome、login、water、water-login、shower、shower-login、profile，省略时打开课表。重新选择页面前先结束演示 Activity 或停止演示包，避免旧页面状态保留。

截图显示示例数据，只用于介绍功能。演示包不要作为正式服务安装包发布。

## Widget 适配

2×2、4×2 是启动器网格规格，实际外形取决于桌面行列与屏幕比例。当前实现按屏幕方向选择宿主宽高；Android 12 及以上对异常偏高的格子限制卡片高度，标准桌面上的正常缩放继续保留。Android 8–11 使用宿主原始尺寸。

## 验证边界

单元测试使用本地模拟服务验证协议与状态处理。模拟器截图不证明真实短信、支付、蓝牙连接或现场开关水成功；发布前应在支持的现场设备上进行相应验证。
