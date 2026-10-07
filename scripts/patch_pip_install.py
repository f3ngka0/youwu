"""对 Chaquopy 的 pip_install.py 打补丁（与 app/build.gradle.kts 里的替换串保持一致）。

单独拆出来，一是为了能在 Gradle 之外手工复现 pip_install 流程做排查，
二是可以先验证每一处 old 串是否真的匹配得上（匹配不上就报 MISS）。

==================== 根因（2026-09-03 用 py-spy 抓栈确认） ====================

栈：
    removedirs (<frozen os>:252)
    remove (chaquopy/pip_install.py:348)        <- remove_empty_dirs 分支
    pip_install (chaquopy/pip_install.py:195)   <- remove(path_abs, remove_empty_dirs=True)

ChaquoPy 有两处会调 os.removedirs 上溯删除空父目录：
  1) remove(..., remove_empty_dirs=True)  里显式调用
  2) os.renames() 内部自带（renames() 只是它的 retry 封装）

单 ABI 构建时，merge_common 会把所有顶层路径都搬进 common/，
于是 arm64-v8a/ 会阶段性变空，上溯删除就一路删上去 ——
实测把 arm64-v8a/chaquopy/（里面全是 native .so）整个删没了，
最终 requirements-*.imy 变成 22 字节的空 zip，Python 依赖根本没进包。
而且本机 rmdir 在这个状态下会永久阻塞，install*PythonRequirements 任务直接挂死。

修复：两处都不做上溯删空目录，只做「建父目录 + rename」。
空目录残留不影响打包（.imy 里多个空条目而已），merge_common 也会自建缺失的 ABI 目录。
==========================================================================
"""
import sys

# 本脚本会被 Gradle 用 env 自带的 python.exe 调用，stdout 可能是 GBK 管道，
# 中文提示直接打出去会 UnicodeEncodeError，这里统一兜住。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

REPLACEMENTS = [
    # ---- 1) remove()：容忍重复删除，并去掉上溯删空目录 ----
    (
        "CHQ-PATCH: remove skipped",
        "def remove(path, remove_empty_dirs=False):\n"
        "    (rmtree if isdir(path) else os.remove)(path)\n"
        "    if remove_empty_dirs:\n"
        "        try:\n"
        "            os.removedirs(dirname(path))\n"
        "        except OSError:\n"
        "            pass  # Directory is not empty.",
        "def remove(path, remove_empty_dirs=False):\n"
        "    try:\n"
        "        (rmtree if isdir(path) else os.remove)(path)\n"
        "    except FileNotFoundError:\n"
        "        print('CHQ-PATCH: remove skipped, already gone: %s' % path, flush=True)\n"
        "    # CHQ-PATCH: 不做 remove_empty_dirs 上溯删空目录，见文件头注释",
    ),
    # ---- 2) 扫描 dist-info 时跳过没有 WHEEL 的残留壳 ----
    (
        'if not (dist_info / "WHEEL").exists()',
        '                wheel_info = email.parser.Parser().parse((dist_info / "WHEEL").open())',
        '                if not (dist_info / "WHEEL").exists():\n'
        "                    continue\n"
        '                wheel_info = email.parser.Parser().parse((dist_info / "WHEEL").open())',
    ),
    # ---- 3) renames()：不再用 os.renames，只在目标已存在时容忍源丢失 ----
    (
        "CHQ-PATCH: renames skipped",
        "def renames(src, dst):\n    os.renames(src, dst)",
        "def renames(src, dst):\n"
        "    # CHQ-PATCH: 不用 os.renames，它内部 os.removedirs 会上溯删空父目录。\n"
        "    _d = os.path.dirname(dst)\n"
        "    if _d and not os.path.exists(_d):\n"
        "        os.makedirs(_d)\n"
        "    try:\n"
        "        os.rename(src, dst)\n"
        "    except FileNotFoundError:\n"
        "        if os.path.exists(dst):\n"
        "            print('CHQ-PATCH: renames skipped, dst already exists: %s' % dst, flush=True)\n"
        "        else:\n"
        "            raise RuntimeError('CHQ-PATCH: renames source missing and dst absent: %s -> %s' % (src, dst))",
    ),
    # ---- 以下为追踪，排查完可整体删除 ----
    (
        "CHQ-TRACE main",
        '            os.mkdir(join(self.target, "common"))',
        '            os.mkdir(join(self.target, "common"))\n'
        "            print('CHQ-TRACE main: target=%s abis=%s' % (self.target, self.android_abis), flush=True)",
    ),
    (
        "CHQ-TRACE move_pure",
        '        logger.debug("Moving pure requirements")',
        '        logger.debug("Moving pure requirements")\n'
        "        print('CHQ-TRACE move_pure: %d pure trees' % len(pure_trees), flush=True)",
    ),
    (
        "CHQ-TRACE dist_info",
        '            for ri in req_infos:\n                common_path = join(self.target, "common", ri.dist_info.name)',
        '            for ri in req_infos:\n'
        "                print('CHQ-TRACE dist_info: %s' % ri.dist_info.name, flush=True)\n"
        '                common_path = join(self.target, "common", ri.dist_info.name)',
    ),
    (
        "CHQ-TRACE merge_common",
        '        logger.debug("Merging ABIs")',
        '        logger.debug("Merging ABIs")\n'
        "        print('CHQ-TRACE merge_common: abis=%s top=%s' "
        "% (list(abi_trees.keys()), sorted(list(abi_trees.values())[0].keys()) if abi_trees else []), flush=True)",
    ),
    (
        "CHQ-TRACE move_to_common",
        "    def move_to_common(self, abi, filename):",
        "    def move_to_common(self, abi, filename):\n"
        "        print('CHQ-TRACE move_to_common: %s | abi_exists=%s common_exists=%s' "
        "% (filename, exists(join(self.target, abi, filename)), exists(join(self.target, 'common', filename))), flush=True)",
    ),
]


def patch(path):
    src = open(path, encoding="utf-8").read()
    out = src
    changed = False
    for marker, old, new in REPLACEMENTS:
        if marker in out:
            continue
        if old not in out:
            print("  MISS  %s  (old 串未匹配)" % marker)
            continue
        out = out.replace(old, new, 1)
        changed = True
        print("  OK    %s" % marker)
    if changed:
        open(path, "w", encoding="utf-8").write(out)
        print("  -> 已写入", path)
    else:
        print("  -> 无变化")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        print("patch", p)
        patch(p)
