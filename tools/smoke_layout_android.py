#!/usr/bin/env python3
"""仅模拟器：布局滑块、窗口尺寸、跨进程保存及球大小；恢复配置，不读取使用记录。"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'dev.sinan.launcher'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    adb = ['adb', '-s', args.serial]

    def run(*cmd):
        return subprocess.check_output(adb + list(cmd), text=True, timeout=30).strip()

    def sh(*cmd):
        return run('shell', *cmd)

    def exists(path):
        return subprocess.run(adb + ['shell', 'run-as', PACKAGE, 'test', '-f', path],
                              capture_output=True, timeout=15).returncode == 0

    def nodes():
        sh('uiautomator', 'dump', '/sdcard/sinan-layout-smoke.xml')
        return list(ET.fromstring(run('exec-out', 'cat', '/sdcard/sinan-layout-smoke.xml')).iter('node'))

    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))

    def tap(node):
        a, b, c, d = bounds(node)
        sh('input', 'tap', str((a + c) // 2), str((b + d) // 2))

    def find(label):
        for _ in range(9):
            tree = nodes()
            found = next((n for n in tree if n.get('text') == label or n.get('content-desc') == label), None)
            if found is not None:
                return found
            scroll = next(n for n in tree if n.get('class') == 'android.widget.ScrollView' and n.get('package') == PACKAGE)
            a, b, c, d = bounds(scroll)
            sh('input', 'swipe', str((a + c) // 2), str(b + (d - b) * 3 // 4),
               str((a + c) // 2), str(b + (d - b) // 4), '350')
        raise AssertionError('找不到控件：' + label)

    def start(settings=True):
        sh('am', 'force-stop', PACKAGE)
        sh('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        time.sleep(1)
        if settings:
            tap(find('设置'))

    def set_slider(title, fraction):
        for _ in range(9):
            tree = nodes()
            node = next((n for n in tree if n.get('class') == 'android.widget.SeekBar'
                         and n.get('content-desc', '').startswith(title + ' · ')), None)
            if node is not None:
                a, b, c, d = bounds(node)
                if d - b >= 35:
                    sh('input', 'tap', str(max(a + 1, min(c - 1, round(a + (c - a) * fraction)))), str((b + d) // 2))
                    time.sleep(0.5)
                    return
            scroll = next(n for n in tree if n.get('class') == 'android.widget.ScrollView' and n.get('package') == PACKAGE)
            a, b, c, d = bounds(scroll)
            sh('input', 'swipe', str((a + c) // 2), str(b + (d - b) * 3 // 4),
               str((a + c) // 2), str(b + (d - b) // 4), '350')
        raise AssertionError('滑块不可见：' + title)

    def windows(ball=False):
        blocks = re.split(r'(?=  Window #\d+ Window\{)', sh('dumpsys', 'window', 'windows'))
        return [b for b in blocks if PACKAGE in b[:250] and 'isOnScreen=true' in b
                and ('ty=APPLICATION_OVERLAY' in b or 'ty=2038' in b)
                and (('SinanFloatingBall' in b[:250]) == ball)]

    def frame(ball=False):
        block, = windows(ball)
        match = re.search(r'(?:mFrame=|\bframe=)\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
        return tuple(map(int, match.groups()))

    def prefs(path):
        return {n.get('name'): n.get('value') for n in ET.fromstring(sh('run-as', PACKAGE, 'cat', path))}

    def shot(name):
        time.sleep(0.3)  # 等待设置页/搜索页的90ms切换动画结束。
        with (output / (name + '.png')).open('wb') as file:
            subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=file, check=True, timeout=15)

    assert sh('getprop', 'ro.kernel.qemu') == '1', '仅允许模拟器'
    assert 'FloatingBallService' not in sh('dumpsys', 'activity', 'services', PACKAGE), '请先关闭悬浮球'
    api = int(sh('getprop', 'ro.build.version.sdk'))
    output = Path(f'app/build/layout-smoke-api{api}')
    output.mkdir(parents=True, exist_ok=True)
    overlay = re.search(r'SYSTEM_ALERT_WINDOW: (\w+)', sh('appops', 'get', PACKAGE, 'SYSTEM_ALERT_WINDOW'))
    overlay = overlay.group(1) if overlay else 'default'
    rotation = {k: sh('settings', 'get', 'system', k) for k in ['accelerometer_rotation', 'user_rotation']}
    font = sh('settings', 'get', 'system', 'font_scale')
    fixed_rotation = sh('wm', 'fixed-to-user-rotation')
    assert fixed_rotation in ['default', 'disabled', 'enabled', 'enabled_if_no_auto_rotation']
    notification = api >= 33 and 'android.permission.POST_NOTIFICATIONS: granted=true' in sh('dumpsys', 'package', PACKAGE)
    paths = ['shared_prefs/' + name + ext for name in ['layout-preferences', 'floating-ball-appearance', 'floating-ball-position']
             for ext in ['.xml', '.xml.bak']]
    backup = 'shared_prefs/layout-smoke-backup'
    sh('am', 'force-stop', PACKAGE)
    sh('run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs')
    sh('run-as', PACKAGE, 'mkdir', backup)
    for path in paths:
        if exists(path):
            sh('run-as', PACKAGE, 'cp', path, backup + '/' + Path(path).name)
    try:
        for path in paths:
            sh('run-as', PACKAGE, 'rm', '-f', path)
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'allow')
        sh('wm', 'fixed-to-user-rotation', 'enabled')
        sh('settings', 'put', 'system', 'accelerometer_rotation', '0')
        sh('settings', 'put', 'system', 'user_rotation', '0')
        start()
        default_frame = frame()
        set_slider('面板宽度', 0)
        small_frame = frame()
        assert small_frame[2] - small_frame[0] < default_frame[2] - default_frame[0], '宽度未生效'
        set_slider('面板高度', 0)
        assert frame()[3] - frame()[1] < small_frame[3] - small_frame[1], '高度未生效'
        set_slider('面板背景不透明度', 0)
        set_slider('悬浮球大小', 1)
        set_slider('应用图标大小', 1)
        set_slider('网格列数', 1)
        saved = prefs('shared_prefs/layout-preferences.xml')
        assert saved == {'panel-width': '70', 'panel-height': '75', 'panel-opacity': '20', 'icon-size': '72', 'columns': '6'}, saved
        assert prefs('shared_prefs/floating-ball-appearance.xml')['size'] == '80'
        shot('settings-small')
        start()
        assert prefs('shared_prefs/layout-preferences.xml') == saved
        tap(find('返回搜索'))
        assert len(windows()) == 1
        tree = nodes()
        icons = [bounds(n) for n in tree if n.get('class') == 'android.widget.ImageView' and n.get('package') == PACKAGE]
        assert icons, '网格图标不可见'
        shot('grid-small-six-columns')
        print('PASS 宽高缩小、背景20%、图标72dp、六列适配、配置跨进程保存', flush=True)
        # 当前设置可能在屏幕边界处被限制；验证放大及大字号横屏仍可返回搜索。
        start()
        set_slider('面板宽度', 1)
        set_slider('面板高度', 1)
        set_slider('面板背景不透明度', 1)
        start()
        tap(find('开启悬浮球'))
        if api >= 33:
            # 先授予模拟器通知权限，再触发应用内确认。
            sh('pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
        buttons = [n for n in nodes() if n.get('text') == '开启悬浮球' and n.get('class') == 'android.widget.Button']
        tap(buttons[-1])
        sh('input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(1)
        a, b, c, d = frame(True)
        density = int(re.search(r'(\d+)', sh('wm', 'density')).group(1)) / 160
        assert abs(c - a - 80 * density) <= 2 and c - a == d - b, '真实球大小不为80dp'
        print('PASS 运行服务使用80dp球窗口', flush=True)
        service_pid = sh('pidof', PACKAGE)
        sh('input', 'tap', str((a + c) // 2), str((b + d) // 2))
        tap(find('设置'))
        set_slider('悬浮球大小', 0)
        sh('input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(1)
        a, b, c, d = frame(True)
        assert abs(c - a - 48 * density) <= 2, '32dp球未保留48dp触摸区'
        assert sh('pidof', PACKAGE) == service_pid, '大小更新重启了进程'
        sh('input', 'tap', str((a + c) // 2), str((b + d) // 2))
        time.sleep(1)
        assert windows() and not windows(True), '小球无法点开面板'
        sh('settings', 'put', 'system', 'font_scale', '1.5')
        sh('settings', 'put', 'system', 'user_rotation', '1')
        time.sleep(2)
        start()
        a, b, c, d = frame()
        assert c - a > d - b, '系统未实际旋转为横屏'
        set_slider('应用图标大小', 0)
        set_slider('网格列数', 0)
        shot('landscape-font150-settings')
        sh('input', 'keyevent', 'KEYCODE_BACK')
        assert windows(), '返回搜索时面板被关闭'
        shot('landscape-font150-grid')
        print('PASS 运行中缩为32dp、48dp触摸区域、横屏150%字号与自动列数', flush=True)
        sh('am', 'force-stop', PACKAGE)
        sh('settings', 'put', 'system', 'font_scale', '1')
        sh('settings', 'put', 'system', 'user_rotation', '0')
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'deny')
        sh('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        tap(find('设置'))
        tap(find('布局与大小'))
        set_slider('应用图标大小', 1)
        set_slider('网格列数', 0.2)
        assert prefs('shared_prefs/layout-preferences.xml')['icon-size'] == '72'
        assert prefs('shared_prefs/layout-preferences.xml')['columns'] == '2'
        tap(next(n for n in nodes() if n.get('resource-id') == 'android:id/button1'))
        assert not windows(), '无权限时出现悬浮窗口'
        shot('fallback-layout')
        print('PASS 无悬浮权限降级页设置、72dp图标与两列', flush=True)
    finally:
        sh('am', 'force-stop', PACKAGE)
        for path in paths:
            sh('run-as', PACKAGE, 'rm', '-f', path)
            saved_path = backup + '/' + Path(path).name
            if exists(saved_path):
                sh('run-as', PACKAGE, 'cp', saved_path, path)
        sh('run-as', PACKAGE, 'rm', '-r', backup)
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', overlay)
        for key, value in rotation.items():
            sh('settings', 'delete' if value == 'null' else 'put', 'system', key, *([] if value == 'null' else [value]))
        sh('settings', 'delete' if font == 'null' else 'put', 'system', 'font_scale', *([] if font == 'null' else [font]))
        sh('wm', 'fixed-to-user-rotation', fixed_rotation)
        if api >= 33 and not notification:
            sh('pm', 'revoke', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
        sh('rm', '-f', '/sdcard/sinan-layout-smoke.xml')
        print('已恢复布局、球外观/位置、权限、字体与旋转；球已关闭', flush=True)


if __name__ == '__main__':
    main()
