#!/usr/bin/env python3
"""模拟器验证可选悬浮球；恢复权限、屏幕设置及原贴边位置，不读取使用记录。"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'dev.sinan.launcher'
TITLE = 'SinanFloatingBall'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--apk', default='app/build/outputs/apk/debug/app-debug.apk')
    args = parser.parse_args()
    adb = [args.adb, '-s', args.serial]

    def run(*cmd):
        return subprocess.check_output(adb + list(cmd), text=True, timeout=30).strip()

    def sh(*cmd):
        return run('shell', *cmd)

    def wait(predicate, message):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.3)
        raise AssertionError(message)

    def window_blocks():
        return re.split(r'(?=  Window #\d+ Window\{)', sh('dumpsys', 'window', 'windows'))

    def balls():
        return [b for b in window_blocks() if TITLE in b[:250] and 'isOnScreen=true' in b]

    def ball_bounds():
        block, = balls()
        match = re.search(r'(?:mFrame=|\bframe=)\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
        assert match, '无法读取悬浮球窗口边界'
        return tuple(map(int, match.groups()))

    def panels():
        return [b for b in window_blocks() if PACKAGE in b[:250] and TITLE not in b[:250]
                and ('ty=APPLICATION_OVERLAY' in b or 'ty=2038' in b) and 'isOnScreen=true' in b]

    def service():
        return 'FloatingBallService' in sh('dumpsys', 'activity', 'services', PACKAGE)

    def nodes():
        sh('uiautomator', 'dump', '/sdcard/sinan-ball-smoke.xml')
        return ET.fromstring(run('exec-out', 'cat', '/sdcard/sinan-ball-smoke.xml'))

    def tap_node(node):
        a, b, c, d = map(int, re.findall(r'\d+', node.get('bounds')))
        assert c > a and d > b, '测试控件不可见'
        sh('input', 'tap', str((a + c) // 2), str((b + d) // 2))

    def tap(text):
        candidates = [n for n in nodes().iter('node') if n.get('text') == text or n.get('content-desc') == text]
        assert candidates, '找不到测试控件：' + text
        # 弹窗标题和确认按钮可能同名，优先真正的按钮。
        tap_node(next((n for n in candidates if n.get('class') == 'android.widget.Button'), candidates[0]))

    def start_settings():
        sh('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', PACKAGE + '.SHOW_SETTINGS', 'true')

    def enable():
        start_settings()
        tap('设置'); tap('开启悬浮球'); tap('开启悬浮球')

    def stop_settings():
        start_settings(); tap('设置'); tap('关闭悬浮球')
        wait(lambda: not balls() and not service(), '设置关闭后资源未释放')

    def tap_ball():
        a, b, c, d = ball_bounds()
        sh('input', 'tap', str((a + c) // 2), str((b + d) // 2))

    def no_ball():
        return not balls() and not service()

    def private_exists(path):
        return subprocess.run(adb + ['shell', 'run-as', PACKAGE, 'sh', '-c', "'test -f " + path + "'"], capture_output=True, timeout=15).returncode == 0

    assert sh('getprop', 'ro.kernel.qemu') == '1', '仅允许模拟器'
    print(run('install', '-r', args.apk), flush=True)
    assert not service(), '请先手动关闭悬浮球再测试'
    api = int(sh('getprop', 'ro.build.version.sdk'))
    original_ops = sh('appops', 'get', PACKAGE, 'SYSTEM_ALERT_WINDOW')
    match = re.search(r'SYSTEM_ALERT_WINDOW: (\w+)', original_ops)
    original_overlay = match.group(1) if match else 'default'
    settings = {key: sh('settings', 'get', 'system', key) for key in ['user_rotation', 'accelerometer_rotation']}
    permission = ''
    if api >= 33:
        permission = next(line for line in sh('dumpsys', 'package', PACKAGE).splitlines() if 'android.permission.POST_NOTIFICATIONS: granted=' in line)
    output = Path(f'app/build/ball-smoke-api{api}')
    output.mkdir(parents=True, exist_ok=True)

    def shot(name):
        time.sleep(1)
        with (output / (name + '.png')).open('wb') as file:
            subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=file, check=True, timeout=15)

    prefs = 'shared_prefs/floating-ball-position.xml'
    backup = 'shared_prefs/ball-smoke-backup'
    sh('am', 'force-stop', PACKAGE)
    sh('run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs')
    sh('run-as', PACKAGE, 'mkdir', backup)  # 遗留备份时拒绝覆盖。
    for path in [prefs, prefs + '.bak']:
        if private_exists(path):
            sh('run-as', PACKAGE, 'cp', path, backup + '/' + Path(path).name)
    try:
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'allow')
        start_settings()
        assert no_ball(), '默认不应开启悬浮球'
        if api >= 33:
            sh('pm', 'revoke', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
            for flag in ['user-set', 'user-fixed']:
                sh('pm', 'clear-permission-flags', PACKAGE, 'android.permission.POST_NOTIFICATIONS', flag)
            enable()
            deny = next(n for n in nodes().iter('node') if n.get('resource-id', '').endswith('/permission_deny_button'))
            tap_node(deny)
            assert no_ball(), '通知拒绝后仍启动了服务'
            enable()
            allow = next(n for n in nodes().iter('node') if n.get('resource-id', '').endswith('/permission_allow_button'))
            tap_node(allow)
            print('PASS 按需通知授权、拒绝不启用、再次授权', flush=True)
        else:
            enable()
        wait(lambda: len(balls()) == 1 and service(), '未开启唯一悬浮球服务')
        details = sh('dumpsys', 'activity', 'services', PACKAGE)
        assert 'isForeground=true' in details, '未以前台服务运行'
        assert 'foregroundId=1001' in details, '前台通知未注册'
        sh('input', 'keyevent', 'KEYCODE_HOME')
        wait(lambda: len(balls()) == 1, '离开应用后悬浮球消失')
        shot('ball')
        tap_ball()
        wait(lambda: len(panels()) == 1 and not balls(), '球点击未打开唯一面板并隐藏球')
        shot('panel')
        sh('input', 'keyevent', 'KEYCODE_BACK')
        wait(lambda: not panels() and len(balls()) == 1, '面板关闭后球未恢复')
        print('PASS 跨应用可见、前台通知、点球开面板、面板与球互斥', flush=True)

        a, b, c, d = ball_bounds()
        width, height = map(int, re.search(r'(\d+)x(\d+)', sh('wm', 'size')).groups())
        sh('input', 'swipe', str((a + c) // 2), str((b + d) // 2), str(width // 3), str(height * 2 // 3), '700')
        wait(lambda: ball_bounds()[0] < width // 4, '松手未贴左边')
        assert not panels(), '拖动误触打开面板'
        saved = ball_bounds()
        stop_settings(); enable()
        wait(lambda: len(balls()) == 1, '关闭后未重新开启')
        wait(lambda: abs(ball_bounds()[1] - saved[1]) < 8 and abs(ball_bounds()[0] - saved[0]) < 8, '重开位置未保留')
        # API 29 镜像的 run-as 调试域拒绝发送 SIGKILL；该镜像改用强制停止。
        # API 35 使用真实 SIGKILL，检查服务自身的 NOT_STICKY 行为。
        if api >= 30:
            pid = sh('pidof', PACKAGE)
            assert pid.isdigit()
            subprocess.run(adb + ['shell', 'run-as', PACKAGE, 'sh', '-c', "'kill -9 " + pid + "'"], capture_output=True, timeout=15, check=True)
        else:
            sh('am', 'force-stop', PACKAGE)
        wait(no_ball, '进程被杀后未清理球与服务')
        time.sleep(2)
        assert no_ball(), '进程被杀后服务自动拉起'
        start_settings(); assert no_ball(), '重新打开应用时擅自恢复球'
        enable(); wait(lambda: len(balls()) == 1, '进程重建后手动开启失败')
        wait(lambda: abs(ball_bounds()[1] - saved[1]) < 8 and abs(ball_bounds()[0] - saved[0]) < 8, '跨进程位置未保留')
        print('PASS 拖动贴边不误触、设置关闭、跨进程位置保留、被杀不自启', flush=True)

        sh('input', 'keyevent', 'KEYCODE_SLEEP')
        wait(lambda: not balls() and service(), '锁屏时球未隐藏或服务丢失')
        sh('input', 'keyevent', 'KEYCODE_WAKEUP')
        sh('wm', 'dismiss-keyguard')
        wait(lambda: len(balls()) == 1, '解锁后球未恢复')
        start_settings()  # 系统设置的敏感页面可能主动隐藏第三方 Overlay，使用自身普通页旋转。
        sh('settings', 'put', 'system', 'accelerometer_rotation', '0')
        sh('settings', 'put', 'system', 'user_rotation', '1')
        if api == 29:
            sh('wm', 'set-user-rotation', 'lock', '1')
        time.sleep(2)
        wait(lambda: len(balls()) == 1, "旋转后未恢复悬浮球")
        a, b, c, d = ball_bounds()
        assert 0 <= a < c <= max(width, height) and 0 <= b < d <= min(width, height), '旋转后球超出屏幕'
        shot('landscape')
        print('PASS 锁屏隐藏、解锁恢复、横屏边界适配', flush=True)

        sh('settings', 'put', 'system', 'user_rotation', '0')
        if api == 29:
            sh('wm', 'set-user-rotation', 'lock', '0')
        time.sleep(1)
        sh('cmd', 'statusbar', 'expand-notifications')
        root = nodes()
        if not any(n.get('text') == '关闭悬浮球' for n in root.iter('node')):
            parents = {child: parent for parent in root.iter('node') for child in parent}
            owner = next(n for n in root.iter('node') if n.get('text') == '司南悬浮球已开启')
            while owner in parents:
                owner = parents[owner]
                expand = next((n for n in owner.iter('node') if n.get('resource-id', '').endswith('/expand_button')), None)
                if expand is not None:
                    tap_node(expand); break
        shot('notification')
        tap('关闭悬浮球')
        wait(no_ball, '通知关闭后资源未释放')
        sh('cmd', 'statusbar', 'collapse')
        print('PASS 通知关闭入口', flush=True)

        enable(); wait(lambda: len(balls()) == 1, '再次开启失败')
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'deny')
        wait(no_ball, '悬浮权限撤销后仍保留服务/窗口')
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'allow')
        start_settings(); assert no_ball(), '权限恢复后擅自重新开启'
        print('PASS 悬浮权限撤销清理、恢复权限后不自启', flush=True)
    except Exception:
        print('诊断：', sh('pidof', PACKAGE) if service() else '无球服务', flush=True)
        print('服务状态：', '\n'.join(line.strip() for line in sh('dumpsys', 'activity', 'services', PACKAGE).splitlines() if any(key in line for key in ['FloatingBallService', 'isForeground=', 'startRequested=', 'restart', 'Restart'])), flush=True)
        print('前台：', '\n'.join(line.strip() for line in sh('dumpsys', 'activity', 'activities').splitlines() if 'ResumedActivity' in line), flush=True)
        shot('failure')
        raise
    finally:
        sh('am', 'force-stop', PACKAGE)
        sh('input', 'keyevent', 'KEYCODE_WAKEUP'); sh('wm', 'dismiss-keyguard')
        sh('cmd', 'statusbar', 'collapse')
        sh('appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', original_overlay)
        if api >= 33:
            sh('pm', 'grant' if 'granted=true' in permission else 'revoke', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
            for flag in ['user-set', 'user-fixed']:
                sh('pm', 'set-permission-flags' if flag.upper().replace('-', '_') in permission else 'clear-permission-flags', PACKAGE, 'android.permission.POST_NOTIFICATIONS', flag)
        for key, value in settings.items():
            if value == 'null':
                sh('settings', 'delete', 'system', key)
            else:
                sh('settings', 'put', 'system', key, value)
        for path in [prefs, prefs + '.bak']:
            sh('run-as', PACKAGE, 'rm', '-f', path)
            saved_path = backup + '/' + Path(path).name
            if private_exists(saved_path):
                sh('run-as', PACKAGE, 'mv', saved_path, path)
        sh('run-as', PACKAGE, 'rmdir', backup)
        sh('rm', '-f', '/sdcard/sinan-ball-smoke.xml')
        start_settings()
    print('全部通过；原权限、旋转及位置已恢复，悬浮球关闭。截图：', output, flush=True)


if __name__ == '__main__':
    main()
