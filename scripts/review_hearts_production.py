#!/usr/bin/env python3
"""Fresh stock-Forge launch of reobfuscated production + separate support JARs, never a pack launch."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import time
import urllib.request
import zipfile
from review_hearts import ROOT, source_identity, stop, enable_subreaper, reap_owned_children

FORGE = '1.20.1-47.4.22'
PREPARATION_DEADLINE = float('inf')


def request(url):
    # Forge's public Maven endpoint rejects Python's default user-agent.
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'curl/8.15.0 DeathDoorNativeReview/1'}), timeout=90)


def fetch(url, path, sha1=None):
    if time.monotonic() >= PREPARATION_DEADLINE: raise RuntimeError('Production preparation exceeded fifteen minutes')
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file() and sha1 and hashlib.sha1(path.read_bytes()).hexdigest() == sha1:
        return
    with request(url) as response:
        data = response.read()
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError('Download checksum mismatch: ' + url)
    path.write_bytes(data)


def allowed(rules):
    result = not rules
    for rule in rules:
        osrule = rule.get('os', {})
        if osrule.get('name', 'linux') != 'linux':
            continue
        if 'arch' in osrule and osrule['arch'] not in ('amd64', 'x86_64'):
            continue
        if rule.get('features'):  # demo, custom-resolution and quick-play are not enabled
            continue
        result = rule['action'] == 'allow'
    return result


def prepare(install, control):
    manifest = json.loads(request('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json').read())
    entry = next(v for v in manifest['versions'] if v['id'] == '1.20.1')
    vanilla_path = install / 'versions/1.20.1/1.20.1.json'
    fetch(entry['url'], vanilla_path, entry['sha1'])
    vanilla = json.loads(vanilla_path.read_text())
    client = vanilla['downloads']['client']
    fetch(client['url'], vanilla_path.with_suffix('.jar'), client['sha1'])
    (install / 'launcher_profiles.json').write_text('{"profiles":{}}')
    url = f'https://maven.minecraftforge.net/net/minecraftforge/forge/{FORGE}/forge-{FORGE}-installer.jar'
    checksum = request(url + '.sha1').read().decode().strip().split()[0]
    if not re.fullmatch('[a-f0-9]{40}', checksum):
        raise RuntimeError('Invalid Forge installer checksum')
    installer = control / 'forge-installer.jar'
    fetch(url, installer, checksum)
    with (control / 'install.log').open('w') as log:
        proc = subprocess.Popen(['java', '-Djava.io.tmpdir=' + str(control / 'tmp'), '-jar', str(installer),
                                 '--installClient', str(install)], cwd=control, stdout=log,
                                stderr=subprocess.STDOUT, start_new_session=True)
        try:
            if proc.wait(timeout=600):
                raise RuntimeError('Stock Forge installation failed; inspect install.log')
        finally:
            stop(proc)
            reap_owned_children()
    forge = json.loads((install / ('versions/1.20.1-forge-47.4.22/1.20.1-forge-47.4.22.json')).read_text())
    assets = control / 'assets'
    index = vanilla['assetIndex']
    indexpath = assets / 'indexes' / (index['id'] + '.json')
    fetch(index['url'], indexpath, index['sha1'])
    # Verify the authoritative index and every reused object; never treat a previous pass as a fixture.
    cache = Path('/home/dev/.gradle/caches/forge_gradle/assets/objects')
    for item in json.loads(indexpath.read_text())['objects'].values():
        h = item['hash']; target = assets / 'objects' / h[:2] / h
        source = cache / h[:2] / h
        if not target.exists() and source.is_file() and hashlib.sha1(source.read_bytes()).hexdigest() == h:
            target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(source, target)
        fetch('https://resources.download.minecraft.net/' + h[:2] + '/' + h, target, h)
    libraries = {}
    for lib in vanilla['libraries'] + forge['libraries']:
        libraries[lib['name']] = lib
    paths = []
    natives = control / 'natives'; natives.mkdir(exist_ok=True)
    for lib in libraries.values():
        if not allowed(lib.get('rules', [])): continue
        downloads = lib.get('downloads', {})
        artifact = downloads.get('artifact')
        if artifact:
            target = install / 'libraries' / artifact['path']
            # Processor-generated Forge outputs can have no URL: require installation to have produced them.
            if artifact.get('url'):
                fetch(artifact['url'], target, artifact.get('sha1'))
            if not target.is_file(): raise RuntimeError('Installed library missing: ' + str(target))
            paths.append(str(target))
        native = lib.get('natives', {}).get('linux')
        if native:
            native = native.replace('${arch}', '64')
            archive = downloads['classifiers'][native]
            target = install / 'libraries' / archive['path']; fetch(archive['url'], target, archive['sha1'])
            with zipfile.ZipFile(target) as z:
                for n in z.namelist():
                    if n.endswith('.so'):
                        (natives / Path(n).name).write_bytes(z.read(n))
    # Forge supplies its patched minecraft module and client-extra artifact. Adding the vanilla
    # client JAR to the game layer creates a split-package minecraft module, not a stock launch.
    return vanilla, forge, assets, natives, paths


def arguments(values, variables):
    output = []
    for arg in values:
        if isinstance(arg, dict):
            if not allowed(arg.get('rules', [])): continue
            arg = arg['value']
        for item in arg if isinstance(arg, list) else [arg]:
            for key, value in variables.items(): item = item.replace('${' + key + '}', str(value))
            if '${' in item: raise RuntimeError('Unresolved launcher argument: ' + item)
            output.append(item)
    return output


def main():
    global PREPARATION_DEADLINE
    enable_subreaper()
    signal.signal(signal.SIGTERM, lambda signum, frame: (_ for _ in ()).throw(KeyboardInterrupt()))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('WITH_MAX', 'absent'), required=True)
    parser.add_argument('--display', type=int, default=395)
    args = parser.parse_args()
    control = ROOT / 'build/heart-production-control'; control.mkdir(parents=True, exist_ok=True)
    (control / 'tmp').mkdir(exist_ok=True)
    game = ROOT / ('build/heart-production-' + args.profile)
    if game.exists(): shutil.rmtree(game)  # this runner owns this explicitly named disposable fixture only
    game.mkdir(); (game / 'mods').mkdir()
    inputs = [ROOT / 'build/libs/better-deaths-door-1.0.0.jar',
              ROOT / 'build/heart-review-control/better-deaths-door-heart-review-support.jar']
    inputs += [ROOT.parent / repo / 'build/libs' / (repo + '-1.0.0.jar')
               for repo in ('better-survival-hud', 'better-gameplay-notices')]
    def identity():
        return hashlib.sha256((source_identity() + ''.join(hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs)).encode()).hexdigest()
    original = identity()
    for p in inputs: shutil.copyfile(p, game / 'mods' / p.name)
    if args.profile != 'absent':
        pin = Path('/home/dev/.gradle/caches/modules-2/files-2.1/curse.maven/mantle-74924/7563777/8a3f948f8c505ee92dae915ec0a1593594eb2667/mantle-74924-7563777.jar')
        if hashlib.sha1(pin.read_bytes()).hexdigest() != '8a3f948f8c505ee92dae915ec0a1593594eb2667': raise RuntimeError('Mantle fixture checksum mismatch')
        shutil.copyfile(pin, game / 'mods/mantle-1.11.104.jar')
        (game / 'config').mkdir(); (game / 'config/mantle-client.toml').write_text('heartRenderer = "WITH_MAX"\n')
    with open('/home/dev/.worklane/forge-cache.lock', 'a+') as lock:
        deadline = time.monotonic() + 1200
        while True:
            try: fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB); break
            except BlockingIOError:
                if time.monotonic() > deadline: raise RuntimeError('Production preparation lock wait exceeded twenty minutes')
                time.sleep(2)
        PREPARATION_DEADLINE = time.monotonic() + 900
        install = control / 'stock-forge'; install.mkdir(exist_ok=True)
        vanilla, forge, assets, natives, paths = prepare(install, control)
        variables = dict(natives_directory=natives, launcher_name='DeathDoorProductionReview', launcher_version='1',
            classpath=os.pathsep.join(paths), library_directory=install / 'libraries', classpath_separator=os.pathsep,
            auth_player_name='HeartReview', version_name='1.20.1-forge-47.4.22', game_directory=game,
            assets_root=assets, assets_index_name=vanilla['assetIndex']['id'], auth_uuid='12000000000000000000000000000002',
            auth_access_token='0', clientid='0', auth_xuid='0', user_type='legacy', version_type='release', user_properties='{}')
        command = ['java', '-Xmx3G', '-XX:ActiveProcessorCount=4', '-Dmixin.debug.countInjections=true', '-Djava.io.tmpdir=' + str(control / 'tmp'),
                   '-Dinjury.review.hearts=' + args.profile, '-Dinjury.review.smoke=true']
        command += arguments(vanilla['arguments']['jvm'] + forge['arguments']['jvm'], variables)
        command += [forge['mainClass']]
        command += arguments(vanilla['arguments']['game'] + forge['arguments']['game'], variables)
        command += ['--width', '1920', '--height', '1080']
        print('PRODUCTION_HEART_INPUT_SHA256', original, 'profile=' + args.profile, flush=True)
        xvfb = child = None
        with (control / ('xvfb-' + args.profile + '.log')).open('w') as xlog:
            try:
                xvfb = subprocess.Popen(['unshare', '-Ur', 'Xvfb', ':' + str(args.display), '-screen', '0', '1920x1080x24',
                    '-nolisten', 'unix', '-nolock', '-listen', 'tcp', '-ac', '-noreset'], stdout=xlog, stderr=subprocess.STDOUT, start_new_session=True)
                for _ in range(50):
                    if xvfb.poll() is not None: raise RuntimeError('Production Xvfb exited')
                    try:
                        with socket.create_connection(('127.0.0.1', 6000 + args.display), timeout=.2): break
                    except OSError: time.sleep(.1)
                else: raise RuntimeError('Production Xvfb unavailable')
                env = dict(os.environ, DISPLAY='127.0.0.1:' + str(args.display), LIBGL_ALWAYS_SOFTWARE='1', TMPDIR=str(control / 'tmp'))
                logpath = control / (args.profile + '.log')
                with logpath.open('w') as log:
                    child = subprocess.Popen(command, cwd=game, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                    code = child.wait(timeout=600)
                stop(child); reap_owned_children(exclude=(xvfb.pid,))
                text = logpath.read_text()
                if code or 'HEART_REVIEW_COMPLETE profile=' + args.profile not in text or 'HEART_REVIEW_FAILED' in text:
                    raise RuntimeError('Production launch review failed; inspect ' + str(logpath))
                if identity() != original: raise RuntimeError('Production source/JAR/provider inputs drifted')
                print('PRODUCTION_HEART_COMPLETE', args.profile, 'reobfuscated=true stockForge=' + FORGE, flush=True)
            finally:
                try: stop(child)
                finally:
                    try: stop(xvfb)
                    finally: reap_owned_children()


if __name__ == '__main__': main()
