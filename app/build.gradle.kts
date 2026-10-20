import java.util.Properties

val waterProperties = Properties().apply {
    val config = rootProject.file("water.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
fun waterConfig(name: String): String = "\"" +
    (waterProperties.getProperty(name) ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.fengkao.timetable"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.fengkao.timetable"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.1"
        buildConfigField("String", "WATER_API_GATEWAY", waterConfig("API_GATEWAY"))
        buildConfigField("String", "WATER_API_CID", waterConfig("API_CID"))

        ndk {
            // debug 保留 x86_64 以便跑模拟器；release 只发 arm64-v8a 真机包。
            // 单 ABI 能省掉一整份 Python 运行时 + OpenSSL（约 20 MB）。
            // （不能和 splits.abi 同时配置，AGP 会直接报冲突，所以这里按 task 名动态选。）
            val isRelease = gradle.startParameter.taskNames.any { it.contains("Release") }
            abiFilters.addAll(
                if (isRelease) listOf("arm64-v8a")
                else listOf("arm64-v8a", "x86_64")
            )
        }
    }

    // 签名：正式密钥库放在项目根的 timetable-release.jks，凭据在 keystore.properties。
    // 未提供密钥时生成未签名 release，绝不使用调试签名冒充正式发布。
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file(
                providers.gradleProperty("releaseSigningProperties").getOrElse("keystore.properties")
            )
            val props = Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            val sf = props.getProperty("storeFile")?.let { p -> rootProject.file(p) }
            if (sf != null && sf.exists()) {
                storeFile = sf
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                logger.lifecycle("未找到 release 密钥库，将生成未签名 release 包")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 私有签名凭据只从本机文件读取。
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "WATER_API_GATEWAY", "\"\"")
            buildConfigField("String", "WATER_API_CID", "\"\"")
        }
    }

    // 注：ABI 分包（splits.abi）与 defaultConfig.ndk.abiFilters 互斥，不能并存。
    // 这里走更直接的"按 buildType 选 ABI"路线：debug 双 ABI，release 只 arm64-v8a。
    // 如果日后需要给模拟器出 release，把上面 defaultConfig.ndk 改成 isRelease 时仍含两个 ABI，
    // 并解除这行注释启用 splits + androidComponents 的 versionCode 偏移。

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 内嵌 Python 运行时 + new-school-sdk（OpenSSL TLS 指纹可通过教务系统登录）
// vendored MIT SDK 剔除了本校适配不使用的验证码识别模型，许可见 vendor/。
chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install(rootProject.file("vendor/school_sdk-1.9.0-py3-none-any.whl").absolutePath)
        }
    }
}

// （若启用 splits.abi，需在此处用 androidComponents 区分各 ABI 的 versionCode，
//  避免相同 versionCode 跨 ABI 时包管理器报冲突。当前走"按 buildType 选 ABI"路线，
//  单 ABI 不需要 versionCode 偏移。）

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // 液态玻璃：SDF 折射 + 色散 + 高光（参考 QWEA0/Liquid-Glass-Android，其 Compose 版为 Kyant0/backdrop）
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)

    // 网络与序列化
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.embedded)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.json.test)
}

// TEMP-WORKAROUND：Chaquopy 的 pip_install.py 在本机会把 Python 依赖搬丢甚至卡死。
// 根因与修复都写在 scripts/patch_pip_install.py 的文件头注释里，这里只负责调用它 ——
// 替换串只有那一份，避免 Kotlin 字符串转义和 Python 源码两处维护走样。
//
// 挂载点说明：只在 install*PythonRequirements 的 doFirst 挂，是因为 env 里的
// pip_install.py 是 install 阶段才解压出来的；extract*PythonBuildPackages 跑完时
// 目标文件可能还不存在，doLast 会静默跳过。
tasks.matching { it.name.endsWith("PythonRequirements") }.configureEach {
    doFirst {
        val script = rootProject.file("scripts/patch_pip_install.py")
        if (!script.exists()) {
            throw org.gradle.api.GradleException("缺少 ${script.absolutePath}，Chaquopy 无法在本机构建")
        }
        for (v in listOf("debug", "release", "demo")) {
            val target = file("build/python/env/$v/Lib/site-packages/chaquopy/pip_install.py")
            if (!target.exists()) continue
            // 用同 env 自带的解释器跑，能找到 target 就说明它一定在。
            val py = file("build/python/env/$v/Scripts/python.exe")
            project.exec {
                commandLine(py.absolutePath, script.absolutePath, target.absolutePath)
                isIgnoreExitValue = false
            }
        }
    }
}
