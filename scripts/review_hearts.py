#!/usr/bin/env python3
"""Bounded isolated native review; holds the workspace Forge lock for every Gradle invocation."""
import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import signal
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ('WITH_MAX', 'NO_MAX', 'DISABLE', 'absent')


def source_identity():
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT)
    digest = hashlib.sha256()
    for name in sorted(set(names.split(b'\0')) - {b''}):
        path = ROOT / os.fsdecode(name)
        digest.update(name + b'\0')
        digest.update(path.read_bytes() if path.is_file() else b'<missing>')
    for repo in ('better-survival-hud', 'better-gameplay-notices'):
        artifact = ROOT.parent / repo / 'build' / 'libs' / (repo + '-1.0.0.jar')
        digest.update(artifact.read_bytes())
    return digest.hexdigest()


def stop(process):
    if process is None or process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=20)


def main():
    # Run cleanup on cancellation as well as failures and timeouts.
    def interrupted(signum, frame):
        raise KeyboardInterrupt('Heart review interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=(*PROFILES, 'all'), default='all')
    parser.add_argument('--forge-version', default='47.4.22')
    parser.add_argument('--display', type=int, default=394)
    args = parser.parse_args()
    output = ROOT / 'build' / 'heart-review-control'
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / 'tmp'
    temporary.mkdir(exist_ok=True)
    # Canonical lockf protocol; bounded waiting does not launch competing cache consumers.
    with open('/home/dev/.worklane/forge-cache.lock', 'a+') as lock:
        deadline = time.monotonic() + 900
        while True:
            try:
                fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() > deadline:
                    raise RuntimeError('Forge cache lock unavailable for fifteen minutes')
                time.sleep(2)
        xvfb = child = None
        with (output / 'xvfb.log').open('w') as xlog:
            try:
                xvfb = subprocess.Popen(['unshare', '-Ur', 'Xvfb', ':' + str(args.display),
                    '-screen', '0', '1920x1080x24', '-nolisten', 'unix', '-nolock',
                    '-listen', 'tcp', '-ac', '-noreset'], stdout=xlog, stderr=subprocess.STDOUT,
                    start_new_session=True)
                for _ in range(50):
                    if xvfb.poll() is not None:
                        raise RuntimeError('Xvfb exited; inspect build/heart-review-control/xvfb.log')
                    try:
                        with socket.create_connection(('127.0.0.1', 6000 + args.display), timeout=.2):
                            break
                    except OSError:
                        time.sleep(.1)
                else:
                    raise RuntimeError('Xvfb did not become ready')
                env = dict(os.environ, DISPLAY='127.0.0.1:' + str(args.display), TMPDIR=str(temporary),
                           LIBGL_ALWAYS_SOFTWARE='1', JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=' + str(temporary))
                identity = source_identity()
                print('Review input SHA-256:', identity, flush=True)
                for profile in PROFILES if args.profile == 'all' else (args.profile,):
                    logpath = output / (profile + '.log')
                    with logpath.open('w') as log:
                        child = subprocess.Popen(['./gradlew', '--no-daemon',
                            '-Djava.io.tmpdir=' + str(temporary), 'runInjuryVisual',
                            '-Pforge_version=' + args.forge_version, '-PinjuryHeartProfile=' + profile],
                            cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                            start_new_session=True)
                        code = child.wait(timeout=1500)
                    text = logpath.read_text()
                    if source_identity() != identity:
                        raise RuntimeError('Source/provider inputs changed during native review')
                    if code or ('HEART_REVIEW_COMPLETE profile=' + profile) not in text or 'HEART_REVIEW_FAILED' in text:
                        raise RuntimeError('Native review failed: ' + profile + '; inspect ' + str(logpath))
                    print('Native renderer observations passed:', profile, flush=True)
            finally:
                stop(child)
                stop(xvfb)


if __name__ == '__main__':
    main()
