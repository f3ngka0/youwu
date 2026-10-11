# 1.1 候选版验证记录

- versionName 1.1、versionCode 9，Android8.0起，正式包仅arm64-v8a。release构建与签名验证通过，不含debuggable标记。
- 正式签名证书SHA-256与GitHub公开版1.0相同；包名相同，版本号从8增至9，具备覆盖升级条件。刚交付的debug测试包签名不同，不能直接覆盖安装正式包。
- 相关业务测试64项通过（洗澡44、组件11、饮水API9），0失败；release lint为0错误、184警告。
- 仅确认compact-long-name.png一张目标图：实际RemoteViews、144dp正方形、字体比例1.15，采用用户提供的长名称样例与演示备注。设备名中间省略、房间编号末尾可见，账户与主按钮没有重叠；未检查其他界面。
- 元数据兼容问题：按[Android官方Kotlin兼容清单](https://developer.android.com/build/kotlin-support)及[R8官方覆盖方法](https://r8.googlesource.com/r8/+/refs/heads/main/README.md)，固定使用官方Maven仓库中的R8 9.1.56。mapping.txt确认实际编译器版本，日志无Kotlin元数据解析警告。构建仍有资源提供器不支持异步解析的提示，构建成功。
- 候选安装包SHA-256：b7d77ca24b93b90566bdb25f097dbe55178b414a4bcab3b1a48ab051be433a0b。
- 未在真实手机确认该布局，也未重新测试真实短信、设备出水和结算。模拟器保持可见，停留唯一目标预览。
- 用户要求先过目更新文档，故未推送本轮修改、未合并PR、未创建tag或GitHub Release，也未上传候选APK。
