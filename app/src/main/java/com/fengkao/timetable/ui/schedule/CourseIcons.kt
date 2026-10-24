package com.fengkao.timetable.ui.schedule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.InterpreterMode
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Satellite
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Storm
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Nature
import androidx.compose.material.icons.outlined.Tsunami
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 课程名 → 矢量图标。
 *
 * 规则匹配用「关键词包含」而不是精确相等：教务导出的课名长得千奇百怪
 * （《毛泽东思想和中国特色社会主义理论体系概论》、高等数学(下)、大学物理实验A……），
 * 用关键词才能覆盖住同一类课。顺序敏感：越具体的规则必须排在越前面，
 * 否则「大学物理实验」会被「物理」先吃掉 Icon，拿不到更贴切的实验图标。
 *
 * 全部匹配不上时返回 null，调用方降级成课程首字（保留原来的课程配色）。
 */
object CourseIcons {

    /** 命中即返回；[keywords] 里任一子串出现在课名里就算命中。 */
    private class Rule(val icon: ImageVector, vararg val keywords: String)

    private val rules: List<Rule> = listOf(
        // —— 数学 / 统计 ——
        Rule(Icons.Outlined.Calculate, "高等数学", "高数", "数学分析", "微积分", "代数", "几何", "微分方程", "离散数学", "数学"),
        Rule(Icons.Outlined.Functions, "线性代数", "矩阵"),
        Rule(
            Icons.Outlined.QueryStats, "概率论", "数理统计", "统计学", "统计", "数据分析", "生物统计",
            "计量经济", "抽样",
        ),
        Rule(Icons.Outlined.Numbers, "数值分析", "计算方法", "数论"),

        // —— 物理 / 化学 / 材料 / 天文 ——
        Rule(Icons.Outlined.Science, "大学物理", "普通物理", "物理实验", "物理学", "物理"),
        Rule(Icons.Outlined.Biotech, "生物化学", "生化", "分子生物", "细胞生物", "微生物", "遗传学", "生物学", "生物"),
        Rule(Icons.Outlined.Bloodtype, "化学实验", "有机化学", "无机化学", "分析化学", "物理化学", "化学"),
        Rule(Icons.Outlined.Spa, "材料", "高分子", "冶金", "复合材料"),
        Rule(Icons.Outlined.Satellite, "天文学", "天体物理", "航天", "航空宇航", "空间"),
        Rule(Icons.Outlined.Storm, "大气科学", "气象", "海洋科学", "海洋"),
        Rule(Icons.Outlined.Terrain, "地质", "地球物理", "地球化学", "地理信息", "测绘"),
        Rule(Icons.Outlined.Water, "水利", "水文", "给排水", "水资源"),
        Rule(Icons.Outlined.Waves, "流体力学", "流体", "船舶"),
        Rule(Icons.Outlined.ElectricBolt, "电磁场", "电工", "电路", "电气", "电机"),
        Rule(Icons.Outlined.Bolt, "电力系统", "高电压", "输配电", "新能源"),

        // —— 计算机 / 电子 / 通信 ——
        Rule(
            Icons.Outlined.Code, "程序设计", "编程", "面向对象", "数据结构", "算法", "软件工程", "软件",
            "代码", "Python", "Java", "C语言", "C++", "Web开发", "前端", "后端",
        ),
        Rule(Icons.Outlined.Terminal, "操作系统", "编译原理", "系统结构", "Linux", "数据库"),
        Rule(Icons.Outlined.Computer, "计算机基础", "计算机", "微机原理", "单片机", "嵌入式"),
        Rule(Icons.Outlined.Storage, "数据库", "数据挖掘", "大数据", "数据仓库", "数据科学"),
        Rule(Icons.Outlined.SmartToy, "人工智能", "机器学习", "深度学习", "神经网络", "模式识别", "智能"),
        Rule(Icons.Outlined.Hub, "计算机网络", "网络", "组网", "网络安全", "信息安全", "密码学"),
        Rule(Icons.Outlined.Memory, "数字电路", "模拟电路", "数电", "模电", "集成电路", "微电子"),
        Rule(Icons.Outlined.Radio, "通信", "信号与系统", "信号处理", "射频", "微波", "天线"),
        Rule(Icons.Outlined.Wifi, "物联网", "传感", "无线"),
        Rule(Icons.Outlined.Devices, "人机交互", "多媒体", "虚拟现实", "图形学"),
        Rule(Icons.Outlined.Animation, "动画", "游戏", "数媒", "数字媒体"),
        Rule(Icons.Outlined.SmartDisplay, "电视", "影视制作", "编导，摄像"),
        Rule(Icons.Outlined.Slideshow, "多媒体技术", "课件"),

        // —— 电子信息大类兜底 ——
        Rule(Icons.Outlined.Router, "电子信息", "电子"),

        // —— 机械 / 建筑 / 土木 ——
        Rule(Icons.Outlined.Engineering, "机械设计", "机械原理", "机械制造", "机械工程", "机械"),
        Rule(Icons.Outlined.PrecisionManufacturing, "数控", "制造", "加工", "模具", "焊接"),
        Rule(Icons.Outlined.DirectionsCar, "汽车", "车辆", "内燃机", "新能源汽车"),
        Rule(Icons.Outlined.Architecture, "建筑设计", "建筑构造", "城市规划", "建筑学", "建筑"),
        Rule(Icons.Outlined.Straighten, "工程制图", "机械制图", "公差", "测量学", "施工"),
        Rule(Icons.Outlined.Construction, "土木工程", "结构力学", "材料力学", "土木", "桥梁", "岩土", "地基"),
        Rule(Icons.Outlined.Apartment, "工程管理", "工程造价", "项目管理"),
        Rule(Icons.Outlined.Factory, "工业工程", "生产管理", "物流工程"),
        Rule(Icons.Outlined.Inventory, "物流", "供应链", "仓储"),
        Rule(Icons.Outlined.LocalShipping, "运输", "交通运输", "铁道", "轨道交通"),
        Rule(Icons.Outlined.Explore, "旅游管理", "旅游", "酒店管理", "会展"),

        // —— 经济 / 管理 / 商务 ——
        Rule(Icons.Outlined.Paid, "财务管理", "会计", "审计", "成本会计", "税法"),
        Rule(Icons.Outlined.AttachMoney, "金融", "经济学", "货币银行", "证券", "投资", "保险"),
        Rule(
            Icons.Outlined.Business, "管理学", "工商管理", "企业管理", "市场营销", "营销", "人力资源",
            "组织行为", "战略",
        ),
        Rule(Icons.Outlined.Receipt, "电子商务", "商务", "贸易", "国际贸易", "报关"),
        Rule(Icons.Outlined.AccountTree, "概率", "决策", "运筹", "博弈"),
        Rule(Icons.Outlined.Insights, "经济", "宏观", "微观", "产业"),
        Rule(Icons.Outlined.Storefront, "连锁经营", "门店", "零售"),
        Rule(Icons.Outlined.Handshake, "谈判", "公共关系", "客户关系"),
        Rule(Icons.Outlined.CalendarMonth, "行政管理", "公共管理", "公共事业"),
        Rule(Icons.Outlined.Event, "活动策划", "会展策划", "策划"),

        // —— 文法史哲 ——
        Rule(Icons.Outlined.Policy, "思想政治", "马克思", "毛泽东", "邓小平", "三个代表", "科学发展观",
            "习近平新时代中国特色社会主义思想", "形势与政策", "思政"),
        Rule(Icons.Outlined.Balance, "法学", "法律", "宪法", "民法", "刑法", "诉讼法", "法理学", "知识产权"),
        Rule(Icons.Outlined.Gavel, "司法", "仲裁", "审判"),
        Rule(Icons.Outlined.Description, "公文写作", "应用文", "写作", "论文"),
        Rule(Icons.Outlined.History, "历史", "史学", "近代史", "党史"),
        Rule(Icons.Outlined.Psychology, "心理", "教育学", "教育心理", "认知"),
        Rule(Icons.Outlined.Translate, "英语", "大学英语", "外语", "日语", "俄语", "法语", "德语", "翻译",
            "听说", "口语", "商务英语"),
        Rule(Icons.Outlined.InterpreterMode, "口译", "同传", "笔译"),
        Rule(Icons.Outlined.Language, "语言学", "语言", "汉语", "古汉语", "现代汉语", "语言文字"),
        Rule(Icons.Outlined.AutoStories, "大学语文", "语文", "文学", "阅读", "经典"),
        Rule(Icons.Outlined.LocalLibrary, "文献检索", "信息检索", "图书馆"),
        Rule(Icons.Outlined.Newspaper, "新闻", "传播", "新闻学", "广告", "编辑出版"),
        Rule(Icons.Outlined.RecordVoiceOver, "播音", "主持", "演讲", "口才"),
        Rule(Icons.Outlined.Groups, "社会学", "社会工作", "人类学", "民族学", "社会工作"),
        Rule(Icons.Outlined.VolunteerActivism, "志愿服务", "公益"),
        Rule(Icons.Outlined.School, "教育", "教师", "教学论", "课程与教学"),

        // —— 艺术 / 设计 / 音乐 / 影视 ——
        Rule(Icons.Outlined.Palette, "美术", "绘画", "造型", "艺术", "油画", "国画", "版画", "陶艺"),
        Rule(Icons.Outlined.Brush, "书法", "国画", "写意", "工笔"),
        Rule(Icons.Outlined.Draw, "素描", "速写", "手绘", "插画", "平面设计", "视觉传达"),
        Rule(Icons.Outlined.ColorLens, "色彩", "构成", "装饰", "服装设计", "染织"),
        Rule(Icons.Outlined.MusicNote, "音乐", "乐理", "声乐", "视唱", "练耳", "民乐", "管弦"),
        Rule(Icons.Outlined.Piano, "钢琴", "键盘", "器乐"),
        Rule(Icons.Outlined.GraphicEq, "舞蹈", "形体", "合唱", "指挥"),
        Rule(Icons.Outlined.Headphones, "录音", "音频", "播音主持"),
        Rule(Icons.Outlined.Theaters, "戏剧", "表演", "戏曲", "话剧"),
        Rule(Icons.Outlined.SmartDisplay, "影视", "摄影摄像", "广播电视"),
        Rule(Icons.Outlined.PhotoCamera, "摄影", "摄像"),
        Rule(Icons.Outlined.Camera, "照相", "影像"),
        Rule(Icons.Outlined.Animation, "动画", "动漫"),

        // —— 体育 / 军事 / 健康 ——
        Rule(Icons.Outlined.FitnessCenter, "体育", "田径", "篮球", "足球", "排球", "乒乓球", "羽毛球",
            "网球", "游泳", "健美", "武术", "太极", "瑜伽", "体操", "体质"),
        Rule(Icons.Outlined.SportsBasketball, "篮球"),
        Rule(Icons.Outlined.SportsSoccer, "足球"),
        Rule(Icons.Outlined.SportsTennis, "网球", "乒乓球", "羽毛球"),
        Rule(Icons.Outlined.SelfImprovement, "瑜伽", "太极", "养生", "心理健康"),
        Rule(Icons.Outlined.DirectionsBike, "自行车", "骑行"),
        Rule(Icons.Outlined.MedicalServices, "临床", "内科", "外科", "妇产", "儿科", "病理", "诊断"),
        Rule(Icons.Outlined.MonitorHeart, "生理", "解剖", "药理", "医学", "护理"),
        Rule(Icons.Outlined.Vaccines, "免疫", "预防医学", "流行病", "公共卫生"),
        Rule(Icons.Outlined.LocalHospital, "康复", "急诊", "全科"),
        Rule(Icons.Outlined.Medication, "药学", "药物", "制药"),
        Rule(Icons.Outlined.LocalPharmacy, "药剂", "调剂"),
        Rule(Icons.Outlined.HealthAndSafety, "卫生", "健康", "营养", "食品安全"),
        Rule(Icons.Outlined.Restaurant, "烹饪", "食品", "餐饮", "营养配餐"),
        Rule(Icons.Outlined.LocalCafe, "茶艺", "茶", "咖啡", "酒水"),
        Rule(Icons.Outlined.WineBar, "酒", "葡萄与葡萄酒"),
        Rule(Icons.Outlined.Nature, "军事理论", "国防", "军事"),

        // —— 农林 / 生态 / 动医 ——
        Rule(Icons.Outlined.Agriculture, "农学", "作物", "栽培", "育种", "农机", "农业"),
        Rule(Icons.Outlined.Grass, "草坪", "牧草", "园林"),
        Rule(Icons.Outlined.Forest, "林学", "森林", "林业"),
        Rule(Icons.Outlined.LocalFlorist, "园艺", "花卉", "插花", "景观"),
        Rule(Icons.Outlined.Grain, "种子", "粮食", "作物育种"),
        Rule(Icons.Outlined.Eco, "生态", "环境科学", "环境工程", "环境保护", "环境"),
        Rule(Icons.Outlined.Pets, "兽医", "动物", "畜牧", "宠物"),
        Rule(Icons.Outlined.EmojiNature, "植物", "昆虫", "生物学基础"),

        // —— 地理 / 海洋 / 气象 ——
        Rule(Icons.Outlined.Map, "地图学", "地图", "地理"),
        Rule(Icons.Outlined.Public, "国际", "世界", "全球"),
        Rule(Icons.Outlined.TravelExplore, "考察", "户外", "野外"),
        Rule(Icons.Outlined.Landscape, "地貌", "自然地理"),
        Rule(Icons.Outlined.Layers, "地层", "沉积", "岩土"),
        Rule(Icons.Outlined.Anchor, "港口", "航海", "港口航道"),
        Rule(Icons.Outlined.Sailing, "航海技术", "轮机"),
        Rule(Icons.Outlined.Tsunami, "灾害", "防灾", "减灾"),

        // —— 交叉 / 通识 / 创新创业 ——
        Rule(Icons.Outlined.Lightbulb, "创新创业", "创新", "创业", "创造学", "TRIZ"),
        Rule(Icons.Outlined.EmojiEvents, "竞赛", "赛事", "职业规划"),
        Rule(Icons.Outlined.Security, "安全工程", "安全管理", "消防"),
        Rule(Icons.Outlined.LocalActivity, "第二课堂", "素质拓展"),
        Rule(Icons.Outlined.Schedule, "时间管理", "生涯规划"),
        Rule(Icons.Outlined.Business, "职业素养", "就业指导", "职业"),
        Rule(Icons.Outlined.School, "入学教育", "新生", "军训"),
    )

    /** 只是为了避免导入名过长而设的别名。 */

    /** 课名 → 图标；未收录返回 null（调用方降级首字）。 */
    fun iconFor(courseName: String): ImageVector? {
        val name = normalize(courseName)
        if (name.isEmpty()) return null
        for (rule in rules) {
            for (keyword in rule.keywords) {
                if (name.contains(keyword)) return rule.icon
            }
        }
        return null
    }

    /** 去掉书名号 / 括号注释 / 空白，避免「高等数学（上）」这种被括号内容干扰。 */
    private fun normalize(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("[《》〈〉<>]"), "")
        s = s.replace(Regex("[（(][^）)]*[）)]"), "")
        s = s.replace(Regex("\\s+"), "")
        return s
    }
}
