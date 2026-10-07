"""new-school-sdk 的 Android 桥接适配层（Chaquopy 调用入口）。

性能策略：
- 登录会话按账号缓存在进程内，跨候选学期/跨次同步复用（一次登录多次取表）；
- 会话失效（取表抛错）时清缓存重登一次再试。
校历：登录成功后顺带从教务系统首页校历面板抓取当前学期开学日期
（形如「2026-2027学年1学期(2026-09-07至2027-01-17)」），失败静默返回空串。
"""

import json
import re
import time
from copy import deepcopy

from school_sdk import SchoolClient
from school_sdk.config import URL_ENDPOINT


HOST = "jwxt.gxuwz.edu.cn"
BASE_PATH = "/jwglxt"
BASE = "https://" + HOST

# 首页第五区块即校历面板（jwglxt_index.js: $("#area_five").load("/xtgl/index_cxAreaFive.html")）
# 注意：sdk 的 BASE_URL 只有主机不含 /jwglxt，相对路径会拼错，这里统一用绝对 URL
_CAL_PATHS = (
    BASE + BASE_PATH + "/xtgl/index_cxAreaFive.html",
    BASE + BASE_PATH + "/xtgl/index_cxAreaFive.html?localeKey=zh_CN",
)
# 校历面板标题形如：2026-2027学年1学期(2026-09-07至2027-01-17)
_CAL_RE = re.compile(r"(\d{4})-(\d{4})学年\s*(\d+)\s*学期\s*[（(]\s*(\d{4}-\d{1,2}-\d{1,2})\s*至")

_USER_CACHE = {}


def _prefix_endpoints(value):
    """SDK 默认端点不带 /jwglxt 前缀，本校部署在 /jwglxt 下。"""
    if isinstance(value, dict):
        return {key: _prefix_endpoints(item) for key, item in value.items()}
    if isinstance(value, str) and value.startswith("/"):
        return BASE_PATH + value
    return value


URL_ENDPOINTS = _prefix_endpoints(deepcopy(URL_ENDPOINT))

# 学校作息表（覆盖到第 12 节晚课，缺失会导致 SDK 解析 KeyError）
SCHEDULE_TIME = {
    "1": [8, 30],
    "2": [9, 20],
    "3": [10, 25],
    "4": [11, 15],
    "5": [14, 40],
    "6": [15, 30],
    "7": [16, 30],
    "8": [17, 20],
    "9": [19, 30],
    "10": [20, 20],
    "11": [21, 10],
    "12": [22, 0],
}


def get_user(account: str, password: str):
    """登录并返回用户会话（进程内缓存，避免每个候选学期都重新登录）。"""
    user = _USER_CACHE.get(account)
    if user is None:
        school = SchoolClient(
            HOST,
            port=443,
            ssl=True,
            exist_verify=False,
            timeout=20,
            url_endpoints=URL_ENDPOINTS,
        )
        user = school.user_login(account.strip(), password)
        _USER_CACHE[account] = user
    return user


def invalidate_user(account: str):
    _USER_CACHE.pop(account, None)


def _fetch_term_start(user, year: int, term: int) -> str:
    """Only accept a calendar start that belongs to the requested semester."""
    for url in _CAL_PATHS:
        try:
            resp = user.get(url, timeout=10)
            m = _CAL_RE.search(resp.text or "")
            if m and int(m.group(1)) == int(year) and int(m.group(3)) == int(term):
                return m.group(4)
        except Exception:
            continue
    return ""


def fetch_schedule(account: str, password: str, year: int, term: int, want_term_start: bool = False) -> str:
    """登录并返回归一化课表 JSON；want_term_start 时附带校历开学日期（term_start 字段）。"""
    if not account or not password:
        raise ValueError("账号和密码不能为空")

    user = get_user(account, password)
    try:
        schedule = user.get_schedule(
            year=int(year),
            term=int(term),
            schedule_time=SCHEDULE_TIME,
        )
    except Exception:
        # 会话可能已过期：清缓存重新登录一次再试
        invalidate_user(account)
        user = get_user(account, password)
        schedule = user.get_schedule(
            year=int(year),
            term=int(term),
            schedule_time=SCHEDULE_TIME,
        )
    if not isinstance(schedule, dict):
        raise RuntimeError("教务系统返回的课表格式无法识别")

    term_start = _fetch_term_start(user, year, term) if want_term_start else ""

    return json.dumps(
        {
            "username": schedule.get("username") or account,
            "class_name": schedule.get("class_name") or "",
            "year": int(year),
            "term": int(term),
            "term_start": term_start,
            "updated_at": int(time.time() * 1000),
            "courses": schedule.get("course_list", []),
        },
        ensure_ascii=False,
    )
