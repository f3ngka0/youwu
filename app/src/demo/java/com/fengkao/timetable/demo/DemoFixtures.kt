package com.fengkao.timetable.demo

import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.CourseNote
import com.fengkao.timetable.data.model.NoteKind
import com.fengkao.timetable.data.xuexitong.NoticeCategory
import com.fengkao.timetable.data.xuexitong.XuexitongNotice
import java.time.LocalDate
import java.util.UUID

/**
 * 演示用虚构数据。
 *
 * 只存在于 .demo 变体：课程名、教师、教室都是编的，但结构照真实教学安排来
 * （同一门课一周两次、实验课连堂 3 节、上午满下午空），否则截图一眼假。
 */
object DemoFixtures {

    private fun course(
        name: String,
        place: String,
        teacher: String,
        day: Int,
        first: Int,
        last: Int,
        weeks: List<Int> = (1..16).toList(),
        weeksText: String = "1-16周",
    ) = CourseBlock(
        name = name,
        place = place,
        teacher = teacher,
        campus = "梧州学院 · 北校区",
        dayOfWeek = day,
        startPeriod = first,
        endPeriod = last,
        weeks = weeks,
        weeksText = weeksText,
        className = "24计算机科学与技术1班",
    )

    /** 计算机科学与技术 大二上：10 门课、19 个课时段，周二/周四各有一次三节连堂实验。 */
    val courses: List<CourseBlock> = listOf(
        course("高等数学（上）", "明理楼 A-201", "陈立群", 1, 1, 2),
        course("大学英语（三）", "明理楼 B-305", "黄晓梅", 1, 3, 4),
        course("数据结构", "明科楼 713（软件室2）", "韦国强", 1, 6, 7),
        course("大学体育（三）", "北校区田径场", "李振华", 1, 8, 9),
        course("线性代数", "明理楼 A-203", "陈立群", 2, 1, 2),
        course("程序设计基础实验", "明科楼 713（软件室2）", "韦国强", 2, 3, 5),
        course("思想道德与法治", "明理楼 C-101", "罗永红", 2, 6, 7),
        course("数据结构", "明科楼 512（计算机室1）", "韦国强", 3, 1, 2),
        course("离散数学", "明理楼 A-305", "覃海燕", 3, 3, 4),
        course("大学物理（下）", "明理楼 B-207", "蒙志远", 3, 6, 7),
        course("数据结构实验", "明科楼 512（计算机室1）", "韦国强", 4, 3, 5),
        course("形势与政策", "明理楼 C-102", "罗永红", 4, 6, 7),
        course("中国传统文化", "图书馆 报告厅", "梁文静", 4, 8, 9, (2..8).toList(), "2-8周"),
        course("大学物理（下）", "明理楼 B-207", "蒙志远", 5, 1, 2),
        course("线性代数", "明理楼 A-203", "陈立群", 5, 3, 4),
        course("大学英语（三）", "明理楼 B-305", "黄晓梅", 5, 6, 7),
        course("创新创业基础", "明理楼 C-201", "杨海波", 5, 10, 11, (3..12).toList(), "3-12周"),
    )

    /**
     * 学习通通知样例：42 条真实数据的缩影。
     *
     * 关键结构照真实数据复刻——同一份作业会发「发布 + 48 小时提醒」两条（靠 taskrefId 合并），
     * 正文里带 `课程名称：` / `作业名称：` 行，调课公告写成「调整到 + 周几 + 第几节 + 地点」。
     */
    fun notices(now: Long = System.currentTimeMillis()): List<XuexitongNotice> {
        val hour = 3_600_000L
        val day = 24 * hour
        fun ago(millis: Long) = now - millis

        return listOf(
            XuexitongNotice(
                id = "n901", title = "《数据结构》第4章作业已发布",
                content = "课程名称：数据结构\n作业名称：第4章 树与二叉树\n" +
                    "开始时间：2026-10-20 08:00\n截止时间：2026-10-27 23:55\n" +
                    "请同学们按时完成并提交。",
                sender = "韦国强", timeText = "4天前", timeMillis = ago(4 * day),
                serverUnread = false, category = NoticeCategory.HOMEWORK,
                attachmentName = "第4章 树与二叉树", attachmentKind = "work",
                attachmentUrl = "https://mooc1.chaoxing.com/work/doHomeWorkNew?taskrefId=90101&x=1",
            ),
            XuexitongNotice(
                id = "n902", title = "作业提交提醒",
                content = "课程名称：数据结构\n作业名称：第4章 树与二叉树\n" +
                    "还有 72 小时 5 分 就要结束，请尽快完成。\n你的作业仍未提交",
                sender = "学习通知", timeText = "昨天 09:12", timeMillis = ago(day + 5 * hour),
                serverUnread = false, category = NoticeCategory.HOMEWORK,
                attachmentName = "第4章 树与二叉树", attachmentKind = "work",
                attachmentUrl = "https://mooc1.chaoxing.com/work/doHomeWorkNew?taskrefId=90101&x=1",
            ),
            XuexitongNotice(
                id = "n903", title = "大学英语（三）：Unit 5 写作任务",
                content = "课程名称：大学英语（三）\n作业名称：Unit 5 Writing Task\n" +
                    "截止时间：2026-10-26 22:00\n字数不少于 200 词，请用英文完成。",
                sender = "黄晓梅", timeText = "今天 08:40", timeMillis = ago(6 * hour),
                serverUnread = true, category = NoticeCategory.HOMEWORK,
                attachmentName = "Unit 5 Writing Task", attachmentKind = "work",
                attachmentUrl = "https://mooc1.chaoxing.com/work/doHomeWorkNew?taskrefId=90207&x=1",
            ),
            XuexitongNotice(
                id = "n904", title = "大学物理（下）期中测验",
                content = "课程名称：大学物理（下）\n考试名称：期中测验\n" +
                    "考试时间：11-05 19:00 至 11-05 21:00\n考试地点：明理楼 B-207",
                sender = "蒙志远", timeText = "2天前", timeMillis = ago(2 * day),
                serverUnread = false, category = NoticeCategory.HOMEWORK,
                attachmentName = "期中测验", attachmentKind = "exam",
                attachmentUrl = "https://mooc1.chaoxing.com/exam/test?taskrefId=90311&x=1",
            ),
            XuexitongNotice(
                id = "n905", title = "【调课通知】数据结构本周四调整到周五",
                content = "课程名称：数据结构\n因任课教师外出参加教学研讨会，本周四第3-5节调整到" +
                    "周五第3-4节，上课地点在明科楼 512（计算机室1）。请同学们互相转告。",
                sender = "韦国强", timeText = "今天 10:05", timeMillis = ago(4 * hour),
                serverUnread = true, category = NoticeCategory.ANNOUNCEMENT,
            ),
            XuexitongNotice(
                id = "n906", title = "【停课】大学物理（下）第 6-7 节",
                content = "课程名称：大学物理（下）\n因实验室设备检修，10月30日（周四）第6-7节停课一次，" +
                    "补课时间另行通知。",
                sender = "蒙志远", timeText = "昨天 16:20", timeMillis = ago(day),
                serverUnread = false, category = NoticeCategory.ANNOUNCEMENT,
            ),
            XuexitongNotice(
                id = "n907", title = "【互调】线性代数与大学英语（三）",
                content = "课程名称：线性代数\n经与黄晓梅老师协商，本周线性代数与大学英语（三）" +
                    "上课时间互换，具体以教务系统为准。",
                sender = "陈立群", timeText = "3天前", timeMillis = ago(3 * day),
                serverUnread = false, category = NoticeCategory.ANNOUNCEMENT,
            ),
            XuexitongNotice(
                id = "n908", title = "手势签到",
                content = "课程名称：离散数学\n覃海燕 发起了手势签到，请在 5 分钟内完成。",
                sender = "覃海燕", timeText = "昨天 09:41", timeMillis = ago(day + 2 * hour),
                serverUnread = false, category = NoticeCategory.SIGNIN,
            ),
            XuexitongNotice(
                id = "n909", title = "关于2026年秋季学期期中教学检查的通知",
                content = "各二级学院：为掌握本学期教学运行情况，定于第 9-10 周开展期中教学检查，" +
                    "请任课教师按时提交教学进度表，学生评教另行通知。",
                sender = "教务处", timeText = "5天前", timeMillis = ago(5 * day),
                serverUnread = false, category = NoticeCategory.ANNOUNCEMENT,
            ),
            XuexitongNotice(
                id = "n910", title = "图书馆数据库使用培训（讲座）",
                content = "时间：10月28日 19:30\n地点：图书馆 报告厅\n" +
                    "内容：中国知网与万方数据的高级检索技巧，现场可领取检索指南。",
                sender = "图书馆", timeText = "6天前", timeMillis = ago(6 * day),
                serverUnread = false, category = NoticeCategory.OTHER,
            ),
            XuexitongNotice(
                id = "n911", title = "《程序设计基础实验》实验报告 3",
                content = "课程名称：程序设计基础实验\n作业名称：实验报告 3 循环结构\n" +
                    "截止时间：2026-11-01 18:00\n提交实验代码与运行截图。",
                sender = "韦国强", timeText = "3天前", timeMillis = ago(3 * day),
                serverUnread = false, category = NoticeCategory.HOMEWORK,
                attachmentName = "实验报告 3 循环结构", attachmentKind = "work",
                attachmentUrl = "https://mooc1.chaoxing.com/work/doHomeWorkNew?taskrefId=90402&x=1",
            ),
        )
    }

    /** 课程笔记：三种类型各一条，其中一条带明天 8:00 的提醒。 */
    fun notes(today: LocalDate, now: Long): List<CourseNote> {
        val remindAt = today.plusDays(1).atTime(8, 0)
            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        return listOf(
            CourseNote(
                id = UUID.randomUUID().toString(), course = "数据结构", kind = NoteKind.HOMEWORK,
                content = "第4章习题 4-1 ~ 4-10，重点：二叉树遍历的非递归写法\n" +
                    "10月27日 23:55 前交到学习通",
                remindAt = remindAt, remindText = "明天 8:00",
                createdAt = now - 2 * 86_400_000L, updatedAt = now - 3_600_000L,
            ),
            CourseNote(
                id = UUID.randomUUID().toString(), course = "数据结构", kind = NoteKind.NOTE,
                content = "AVL 树四种旋转：LL（右旋）、RR（左旋）、LR（先左后右）、RL（先右后左）。\n" +
                    "判断口诀：从失衡点往插入点走两步，方向一致就是单旋，不一致就是双旋。",
                createdAt = now - 4 * 86_400_000L, updatedAt = now - 4 * 86_400_000L,
            ),
            CourseNote(
                id = UUID.randomUUID().toString(), course = "数据结构", kind = NoteKind.POINT,
                content = "考点：\n1. 二叉树前中后序遍历与层序遍历\n2. 哈夫曼树的构造与编码长度计算\n" +
                    "3. B 树与 B+ 树的区别（大题常考）",
                createdAt = now - 86_400_000L, updatedAt = now - 86_400_000L,
            ),
            CourseNote(
                id = UUID.randomUUID().toString(), course = "大学物理（下）", kind = NoteKind.POINT,
                content = "期中测验范围：第 9-12 章（静电场、稳恒磁场、电磁感应、麦克斯韦方程组）\n" +
                    "11月5日 19:00 明理楼 B-207，闭卷，可带计算器",
                remindAt = today.plusDays(1).atTime(19, 30)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                remindText = "明天 19:30",
                createdAt = now - 86_400_000L, updatedAt = now - 86_400_000L,
            ),
        )
    }
}
