#!/usr/bin/env python3
"""在已启动的模拟器上验证系统入口与 T9；不连接真机、不清除数据。"""
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
        node = next((n for n in nodes if n.get("text") == text), None)
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
            key = match.group(1) if match else node.get("text")
            if key in list("0123456789") + ["清空", "退格"]:
                a, b, c, d = map(int, re.findall(r"\d+", node.get("bounds")))
                points[key] = (str((a + c) // 2), str((b + d) // 2))
        assert len(points) == 12, "九宫格按键不可见"

        def tap(key):
            shell("input", "tap", *points[key])

        def digits(value):
            for digit in value:
                tap(digit)

        def assert_query(value):
            if value:
                assert any(n.get("content-desc") == "搜索数字：" + value for n in own_nodes()), "查询未正确更新"
            else:
                assert any(n.get("text") == "输入数字，快速找应用" for n in own_nodes()), "查询未清空"

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
            tap("清空")
            assert_query("")
            digits("25625")
            tap("清空")
            digits("99999")
            tap("清空")
        digits("25625")
        assert_query("25625")
        nodes = own_nodes()
        assert any(n.get("text") == "Clock" for n in nodes), "T9 未匹配 Clock"
        assert not any(n.get("text") == "Camera" for n in nodes), "网格仍包含不匹配应用"
        assert not any(n.get("class") == "android.widget.EditText" for n in nodes)
        shot("t9-fallback-query" if check_editing else "t9-overlay-query")
        print("PASS T9 过滤" + ("、0/1、退格/长按清空、无结果、快速改输" if check_editing else "（悬浮面板）"))

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
        search_clock(check_editing=True)
        tap_text("Clock")
        wait(clock_open, "降级网格未启动 Clock")
        print("PASS 无权限降级、权限引导返回、网格启动")

        allowed("allow")
        open_overlay()
        shot("overlay")
        for _ in range(3):
            start()
        assert overlay_count() == 1, "重复唤出创建了多个窗口"
        shell("input", "keyevent", "KEYCODE_BACK")
        check_closed()
        open_overlay()
        heading = next(n for n in own_nodes() if n.get("text") == "司南")
        left, top, right, _ = map(int, re.findall(r"\d+", heading.get("bounds")))
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
        shell("rm", "-f", "/sdcard/sinan-smoke.xml")
        start()
    print("全部通过；测试权限和磁贴已恢复。截图:", output)


if __name__ == "__main__":
    main()
