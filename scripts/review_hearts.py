#!/usr/bin/env python3
"""Bounded isolated native review; holds the Forge lock per invocation and yields between profiles."""
import argparse
import ctypes
import fcntl
import hashlib
import os
from pathlib import Path
import signal
import shutil
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


def enable_subreaper():
    if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0):
        raise RuntimeError('Cannot establish owned-process subreaper')


def reap_owned_children(exclude=()):
    """Detached descendants are adopted by this dedicated runner, never another agent's processes."""
    def children():
        return set(map(int, Path('/proc/self/task/' + str(os.getpid()) + '/children').read_text().split())) - set(exclude)
    for sig, seconds in ((signal.SIGTERM, 20), (signal.SIGKILL, 10)):
        deadline = time.monotonic() + seconds
        while True:
            remaining = children()
            if not remaining: return
            for pid in remaining:
                try: os.kill(pid, sig)
                except ProcessLookupError: pass
                try: os.waitpid(pid, os.WNOHANG)
                except ChildProcessError: pass
            if time.monotonic() >= deadline: break
            time.sleep(.1)
    if children(): raise RuntimeError('Owned descendant cleanup incomplete: ' + str(children()))


def stop(process):
    if process is None: return
    try: os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError: pass
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        process.wait(timeout=10)


def main():
    enable_subreaper()
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
                           LIBGL_ALWAYS_SOFTWARE='1', JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Dmixin.debug.countInjections=true -Djava.io.tmpdir=' + str(temporary))
                identity = source_identity()
                print('Review input SHA-256:', identity, flush=True)
                for number, profile in enumerate(PROFILES if args.profile == 'all' else (args.profile,)):
                    if number:
                        # Give queued peers an acquisition opportunity without interrupting an active profile.
                        fcntl.lockf(lock, fcntl.LOCK_UN)
                        time.sleep(5)
                        deadline = time.monotonic() + 1200
                        while True:
                            try:
                                fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB); break
                            except BlockingIOError:
                                if time.monotonic() > deadline: raise RuntimeError('Profile lock wait exceeded twenty minutes')
                                time.sleep(2)
                        if source_identity() != identity: raise RuntimeError('Inputs changed while yielding the Forge lock')
                    game = ROOT / ('build/heart-review-' + profile)
                    if game.exists(): shutil.rmtree(game)  # this runner's declared disposable fixture only
                    logpath = output / (profile + '.log')
                    with logpath.open('w') as log:
                        child = subprocess.Popen(['./gradlew', '--no-daemon',
                            '-Djava.io.tmpdir=' + str(temporary), 'runInjuryVisual',
                            '-Pforge_version=' + args.forge_version, '-PinjuryHeartProfile=' + profile],
                            cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                            start_new_session=True)
                        code = child.wait(timeout=1500)
                    stop(child)
                    reap_owned_children(exclude=(xvfb.pid,))
                    text = logpath.read_text()
                    if source_identity() != identity:
                        raise RuntimeError('Source/provider inputs changed during native review')
                    if code or ('HEART_REVIEW_COMPLETE profile=' + profile) not in text or 'HEART_REVIEW_FAILED' in text:
                        raise RuntimeError('Native review failed: ' + profile + '; inspect ' + str(logpath))
                    print('Native renderer observations passed:', profile, flush=True)
            finally:
                try: stop(child)
                finally:
                    try: stop(xvfb)
                    finally: reap_owned_children()


if __name__ == '__main__':
    main()
