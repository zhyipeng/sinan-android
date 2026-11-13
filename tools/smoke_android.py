#!/usr/bin/env python3
"""在模拟器验证入口、T9 和本地排序；在应用私有目录备份/恢复使用汇总。"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "dev.sinan.launcher"
COMPONENT = PACKAGE + "/.MainActivity"
TILE = PACKAGE + "/.SinanTileService"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def run(*cmd):
        return subprocess.check_output(adb + list(cmd), text=True, timeout=30).strip()

    def shell(*cmd):
        return run("shell", *cmd)

    usage_path = "no_backup/usage-summary.bin"
    usage_backup = "no_backup/usage-smoke-backup"
    usage_files = ["usage-summary.bin", "usage-summary.bin.bak", "usage-summary.bin.new"]

    def private_exists(path):
        # Android 10 的 test 是 shell 内建命令，run-as 不能直接 exec 它。
        return subprocess.run(adb + ["shell", "run-as", PACKAGE, "sh", "-c", "'test -f " + path + "'"], capture_output=True, timeout=15).returncode == 0

    def usage_removed():
        return not any(private_exists("no_backup/" + name) for name in usage_files)

    def wait(predicate, message):
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.3)
        raise AssertionError(message)

    def overlay_count():
        windows = shell("dumpsys", "window", "windows")
        blocks = re.split(r"(?=  Window #\d+ Window\{)", windows)
        return sum(
            PACKAGE in block[:250]
            and ("ty=APPLICATION_OVERLAY" in block or "ty=2038" in block)
            and "isOnScreen=true" in block
            for block in blocks
        )

    def own_nodes():
        shell("uiautomator", "dump", "/sdcard/sinan-smoke.xml")
        root = ET.fromstring(run("exec-out", "cat", "/sdcard/sinan-smoke.xml"))
        return [n for n in root.iter("node") if n.get("package") == PACKAGE]

    def tap_text(text):
        nodes = own_nodes()
        node = next((n for n in nodes if (n.get("text") == text or n.get("content-desc") == text)), None)
        assert node is not None, "找不到测试控件: " + text
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def start():
        result = shell("am", "start", "-W", "-n", COMPONENT)
        assert "Status: ok" in result, "入口启动失败"

    def stop():
        shell("am", "force-stop", PACKAGE)

    def allowed(value):
        shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", value)

    def check_closed():
        wait(lambda: overlay_count() == 0, "悬浮窗口未释放")

    def open_overlay():
        start()
        wait(lambda: overlay_count() == 1, "未显示唯一悬浮窗口")

    def clock_open():
        return any("ResumedActivity" in line and "deskclock" in line for line in shell("dumpsys", "activity", "activities").splitlines())

    assert shell("getprop", "ro.kernel.qemu") == "1", "只允许在模拟器上修改测试权限"
    print(run("install", "-r", args.apk))
    before = shell("appops", "get", PACKAGE, "SYSTEM_ALERT_WINDOW")
    match = re.search(r"SYSTEM_ALERT_WINDOW: (\w+)", before)
    original = match.group(1) if match else "default"
    tiles_before = shell("settings", "get", "secure", "sysui_qs_tiles")
    added_tile = TILE not in tiles_before
    output = Path("app/build") / ("smoke-api" + shell("getprop", "ro.build.version.sdk"))
    output.mkdir(parents=True, exist_ok=True)

    def shot(name):
        time.sleep(2)  # 等待窗口动画和异步图标加载，避免保存过渡帧。
        with (output / (name + ".png")).open("wb") as file:
            subprocess.run(adb + ["exec-out", "screencap", "-p"], stdout=file, check=True, timeout=15)

    def search_clock(check_editing):
        points = {}
        for node in own_nodes():
            match = re.match(r"数字 ([0-9])，", node.get("content-desc", ""))
            key = match.group(1) if match else ("退格" if node.get("content-desc") == "退格，长按清空" else ("设置" if node.get("content-desc") == "设置" else node.get("text")))
            if key in list("0123456789") + ["设置", "退格"]:
                a, b, c, d = map(int, re.findall(r"\d+", node.get("bounds")))
                points[key] = (str((a + c) // 2), str((b + d) // 2))
        assert len(points) == 12, "九宫格按键不可见"

        def tap(key):
            shell("input", "tap", *points[key])

        def clear():
            x, y = points["退格"]
            shell("input", "swipe", x, y, x, y, "800")

        def digits(value):
            for digit in value:
                tap(digit)

        def assert_query(value):
            if value:
                assert any(n.get("content-desc") == "搜索数字：" + value for n in own_nodes()), "查询未正确更新"
            else:
                assert any(n.get("content-desc") == "搜索数字：" for n in own_nodes()), "查询未清空"

        if check_editing:
            digits("10")
            assert_query("10")
            tap("退格")
            assert_query("1")
            x, y = points["退格"]
            shell("input", "swipe", x, y, x, y, "800")
            assert_query("")
            digits("00000000")
            assert_query("00000000")
            assert any(n.get("text") == "没有匹配，试试退格或清空" for n in own_nodes()), "无结果反馈缺失"
            shot("t9-no-results")
            clear()
            assert_query("")
            digits("25625")
            clear()
            digits("99999")
            clear()
        digits("25625")
        assert_query("25625")
        nodes = own_nodes()
        assert any(n.get("class") == "android.widget.ImageButton" and n.get("content-desc") == "设置" for n in nodes), "设置未使用图标"
        assert any(n.get("class") == "android.widget.ImageButton" and n.get("content-desc") == "退格，长按清空" for n in nodes), "退格未使用图标"
        assert not any(n.get("text") == "输入数字，快速找应用" or "个应用，按" in n.get("text", "") or n.get("text", "").startswith("显示 ") for n in nodes), "多余提示或数量栏仍可见"
        bounds = lambda n: tuple(map(int, re.findall(r"\d+", n.get("bounds"))))
        query_bounds = bounds(next(n for n in nodes if n.get("content-desc") == "搜索数字：25625"))
        key_bounds = bounds(next(n for n in nodes if n.get("content-desc", "").startswith("数字 1，")))
        grid_bounds = bounds(next(n for n in nodes if n.get("class") == "android.widget.GridView"))
        assert query_bounds[3] <= key_bounds[1], "数字未位于键盘上方"
        assert grid_bounds[3] <= query_bounds[1] or grid_bounds[2] <= query_bounds[0], "数字栏与应用网格重叠"
        assert any(n.get("text") == "Clock" for n in nodes), "T9 未匹配 Clock"
        assert not any(n.get("text") == "Camera" for n in nodes), "网格仍包含不匹配应用"
        assert not any(n.get("class") == "android.widget.EditText" for n in nodes)
        shot("t9-fallback-query" if check_editing else "t9-overlay-query")
        print("PASS T9 过滤" + ("、0/1、退格/长按清空、无结果、快速改输" if check_editing else "（Clock）"))

    def first_app():
        grid = next(n for n in own_nodes() if n.get("class") == "android.widget.GridView")
        return next(n.get("content-desc") for n in grid.iter("node") if n.get("content-desc"))

    def clear_usage():
        tap_text("使用记录")
        tap_text("清空记录")
        wait(usage_removed, "使用记录未删除")
        wait(lambda: first_app() == "Calendar", "清空后未恢复镜像的名称首项 Calendar")

    # 不把已有使用汇总读到电脑或输出中；备份只在应用自己的 no_backup 目录。
    stop()
    shell("run-as", PACKAGE, "mkdir", "-p", "no_backup")
    shell("run-as", PACKAGE, "mkdir", usage_backup)
    for name in usage_files:
        if private_exists("no_backup/" + name):
            shell("run-as", PACKAGE, "cp", "no_backup/" + name, usage_backup + "/" + name)

    try:
        stop()
        allowed("deny")
        start()
        assert any(n.get("text") == "允许悬浮显示" for n in own_nodes())
        assert overlay_count() == 0
        shot("fallback")
        tap_text("允许悬浮显示")
        wait(lambda: any("ResumedActivity" in line and "com.android.settings" in line for line in shell("dumpsys", "activity", "activities").splitlines()), "权限引导未进入设置")
        shell("input", "keyevent", "KEYCODE_BACK")
        clear_usage()
        cold_first = first_app()
        assert cold_first != "Clock", "测试镜像需要默认名称顺序中有早于 Clock 的应用"
        search_clock(check_editing=True)
        tap_text("Clock")
        wait(clock_open, "降级网格未启动 Clock")
        print("PASS 无权限降级、权限引导返回、网格启动")

        start()
        assert first_app() == "Clock", "启动后未学习默认推荐"
        assert private_exists(usage_path), "汇总未落盘"
        shot("usage-learned")
        stop()
        start()
        assert first_app() == "Clock", "跨进程后未保留推荐"
        clear_usage()
        assert first_app() == cold_first, "清空后冷启动顺序不稳定"
        stop()
        subprocess.run(adb + ["shell", "run-as", PACKAGE, "tee", usage_path], input=b"invalid usage fixture", capture_output=True, check=True, timeout=15)
        start()
        assert first_app() == cold_first, "损坏记录未降级"
        tap_text("使用记录")
        shot("usage-settings")
        tap_text("清空记录")
        wait(usage_removed, "损坏记录未清空")
        search_clock(check_editing=False)
        tap_text("Clock")
        wait(clock_open, "清空后未恢复启动")
        start()
        assert first_app() == "Clock", "清空损坏记录后未恢复学习"
        print("PASS 常用排序学习、跨进程保留、清空、损坏降级与恢复")

        allowed("allow")
        stop()  # 推荐验证停留在普通页面，重新从桌面入口检查授权后的路由。
        open_overlay()
        assert not any(n.get("text") in ["司南", "设置", "关闭"] for n in own_nodes()), "面板顶部控件未移除"
        shot("overlay")
        for _ in range(3):
            start()
        assert overlay_count() == 1, "重复唤出创建了多个窗口"
        shell("input", "keyevent", "KEYCODE_BACK")
        check_closed()
        open_overlay()
        panel_root = own_nodes()[0]
        left, top, right, _ = map(int, re.findall(r"\d+", panel_root.get("bounds")))
        # 横屏时导航栏可能在左侧，不能使用固定的屏幕坐标。
        shell("input", "tap", str((left + right) // 2), str(int(top * 0.4)))
        check_closed()
        open_overlay()
        shell("input", "keyevent", "KEYCODE_HOME")
        check_closed()
        open_overlay()
        search_clock(check_editing=False)
        tap_text("Clock")
        check_closed()
        wait(clock_open, "悬浮网格未启动 Clock")
        print("PASS 重复唤出、返回、外部点击、Home、启动后收起")

        shell("cmd", "statusbar", "add-tile", TILE)
        time.sleep(1)
        shell("cmd", "statusbar", "click-tile", TILE)
        wait(lambda: overlay_count() == 1, "磁贴未成功唤出面板")
        tap_text("设置")
        check_closed()
        assert any(n.get("text") == "打开悬浮面板" for n in own_nodes())
        print("PASS 快捷设置磁贴、面板设置入口")

        stop()
        allowed("deny")
        start()
        assert overlay_count() == 0
        assert any(n.get("text") == "允许悬浮显示" for n in own_nodes())
        print("PASS 撤销权限后降级")
    finally:
        stop()
        allowed(original)
        if added_tile:
            shell("cmd", "statusbar", "remove-tile", TILE)
        for name in usage_files:
            shell("run-as", PACKAGE, "rm", "-f", "no_backup/" + name)
            if private_exists(usage_backup + "/" + name):
                shell("run-as", PACKAGE, "mv", usage_backup + "/" + name, "no_backup/" + name)
        shell("run-as", PACKAGE, "rmdir", usage_backup)
        shell("rm", "-f", "/sdcard/sinan-smoke.xml")
        start()
    print("全部通过；测试权限、磁贴和原使用汇总已恢复。截图:", output)


if __name__ == "__main__":
    main()
