# 截图与演示数据说明

截图采集日期：2026-10-07。运行环境为可见的 Android 15 模拟器，屏幕 1080×2400，桌面为 Launcher3。

所有功能截图来自独立的 `com.fengkao.timetable.demo` 演示安装包，复用正式应用界面。演示包移除了联网和蓝牙权限，不读取正式应用的私有目录，也没有真实短信、登录或设备控制操作。

## 虚构数据

- 姓名：演示同学；学号：DEMO-STUDENT；学院、专业与班级均带有示例或演示标记。
- 课程：高等数学、大学英语、程序设计基础等示例课程，教师使用示例教师甲、乙等称呼。
- 饮水设备：DEMO-WATER 系列示例编号。
- 洗澡设备：DEMO-SHOWER 系列示例编号和虚构设备码。
- 余额：20.00 元演示余额，不属于真实账户。
- 课程异动、课程分类、重复事务和课表统计均由虚构课表生成。

状态栏采用 Android 的截图演示模式。功能截图是模拟器直接截图，没有覆盖真实姓名、替换文字或拼接功能画面。

## 界面清单

| 功能 | 图片 |
| --- | --- |
| 首次设置 | welcome.png、welcome-preferences.png |
| 教务登录 | login.png |
| 周课表、详情 | schedule.png、course-detail.png |
| 切周、搜索、添加与管理课程 | week-selection.png、course-search.png、add-course.png、course-management.png、course-menu.png |
| 异动与统计 | insights.png、statistics.png、course-types.png、health-stats.png、free-time.png |
| 喝水登录、默认设备与管理 | water-login.png、water.png、water-devices.png |
| 洗澡登录、余额与设备管理 | shower-login.png、shower.png、shower-devices.png |
| 我的与账户管理 | profile.png、service-accounts.png |
| 课表、学期、作息、同步设置 | schedule-settings.png、term-settings.png、period-settings.png、sync-settings.png |
| 关于与版本 | about.png |
| 深色主题 | schedule-dark.png、shower-dark.png |
| 组件状态 | widget-soon.png、widget-active.png、widget-idle.png |
| Launcher3 实际桌面组件 | widget-desktop.png |

图片均位于 [screenshots](screenshots/) 目录。

## Widget 比例验证

组件状态图来自应用内标准比例预览，使用与桌面相同的 RemoteViews 渲染。2×2 预览为 1:1，4×2 预览为 2:1。应用自带的组件选择器预览图也由这些虚构课程画面裁切生成，不使用个人课表。

实际桌面截图是在 Launcher3 内真正放入两种演示组件后采集的。该启动器的 5×5 网格单元偏高；Android 12 及以上在异常偏高区域限制卡片高度，使卡片不再填满过高的宿主范围。启动器仍保留原有网格占位。横竖屏宽度选取和异常高度处理有单元测试覆盖。

模拟器截图只能验证界面、布局与演示状态。现场蓝牙连接、平台短信、订单结算和设备出水仍需要相应账户与真实设备验证。

## 2026-10-11 更新

本轮仅更新洗澡一级页截图（shower.png），包含虚构双账户和多设备示例。它展示开水账户选择、卡片下方余额及一级设备切换。其他截图来自此前版本，不代表本轮全部界面已验证。
