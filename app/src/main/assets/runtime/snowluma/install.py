#!/usr/bin/env python3
"""Install the official SnowLuma Linux ARM64 distribution for personal use.

Only the official release archive is fetched at the user's request, verified
against the pinned SHA-256 in sources.json and extracted into this manager's
extension directory. No SnowLuma program, native component or modified copy is
bundled with the APK or redistributed. SnowLuma is source-available under a
non-commercial license; see the official LICENSE and EULA before using it.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request

BASE = Path('/manager/extensions/snowluma')
SOURCE = BASE / 'scripts'
DESKTOP = Path('/manager/extensions/desktop')
PROFILE = DESKTOP / 'compat-profile.json'
PROGRAM = BASE / 'program'
STAGING = BASE / 'program.new'
PREVIOUS = BASE / 'program.old'
ARCHIVE = BASE / 'package.tar.gz'
MARKER = BASE / 'installed.json'
STAGES = ('download', 'verify', 'extract', 'configure', 'verify_program')


class Progress:
    def __init__(self):
        self.started = time.monotonic()
        self.last = 0.0

    def emit(self, stage, force=False, **values):
        now = time.monotonic()
        if not force and now - self.last < 0.3:
            return
        self.last = now
        step = STAGES.index(stage) + 1 if stage in STAGES else len(STAGES)
        print('ASTRBOT_SNOWLUMA ' + json.dumps(dict(stage=stage, step=step,
            elapsed=int(now - self.started), **values)), flush=True)


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.new')
    temporary.write_text(json.dumps(data, indent=2) + '\n')
    temporary.chmod(0o600)
    os.replace(temporary, path)


def _download_once(asset, progress):
    expected = asset['bytes']
    digest = hashlib.sha256()
    partial = ARCHIVE.with_suffix('.part')
    offset = partial.stat().st_size if partial.is_file() else 0
    if offset > expected:
        partial.unlink()
        offset = 0
    if offset == expected:
        progress.emit('download', downloaded=expected, total=expected, force=True)
        os.replace(partial, ARCHIVE)
        return
    if offset:
        # A resumed download must hash the bytes already on disk.
        with partial.open('rb') as existing:
            while block := existing.read(65536):
                digest.update(block)
    headers = {'User-Agent': 'AstrBot-for-Android'}
    if offset:
        headers['Range'] = f'bytes={offset}-'
    request = urllib.request.Request(asset['url'], headers=headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        if offset and response.status == 206:
            assert response.headers.get('Content-Range', '').startswith(f'bytes {offset}-'), 'Invalid range response'
        else:
            if offset:
                # The server ignored Range: start the file over with a fresh digest.
                offset = 0
                digest = hashlib.sha256()
            assert response.status == 200, 'Unexpected download response'
        with partial.open('ab' if offset else 'wb') as output:
            received = 0
            while block := response.read(65536):
                received += len(block)
                if received + offset > expected:
                    raise ValueError('Download is larger than the pinned asset')
                output.write(block)
                digest.update(block)
                progress.emit('download', downloaded=offset + received, total=expected)
    if partial.stat().st_size != expected:
        partial.unlink(missing_ok=True)
        raise ValueError('Download was interrupted; retry to download again')
    progress.emit('download', downloaded=expected, total=expected, force=True)
    os.replace(partial, ARCHIVE)


def verify_archive(asset):
    if ARCHIVE.stat().st_size != asset['bytes']:
        raise ValueError('Archive size does not match the pinned release')
    with ARCHIVE.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != asset['sha256']:
        ARCHIVE.unlink(missing_ok=True)
        raise ValueError('Archive checksum does not match the pinned release; download again')


def archive_matches(asset):
    if not ARCHIVE.is_file() or ARCHIVE.stat().st_size != asset['bytes']:
        return False
    with ARCHIVE.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() == asset['sha256']


def download(asset, progress):
    # A dropped mobile connection leaves a partial file; retries resume it.
    for attempt in range(1, 4):
        try:
            _download_once(asset, progress)
            return
        except (OSError, urllib.error.URLError) as error:
            if attempt == 3:
                raise
            print(f'Download attempt {attempt} failed: {error}; retrying in 3 seconds', flush=True)
            time.sleep(3)


def extract(archive, destination, progress):
    shutil.rmtree(destination, ignore_errors=True)
    destination.mkdir(parents=True)
    root = destination.resolve()
    with tarfile.open(archive) as tar:
        members = tar.getmembers()
        for member in members:
            if not (member.isfile() or member.isdir()):
                raise ValueError(f'Unsupported archive entry: {member.name}')
            path = (root / member.name).resolve()
            if path != root and root not in path.parents:
                raise ValueError(f'Archive entry escapes the program directory: {member.name}')
        last = 0.0
        for index, member in enumerate(members):
            tar.extract(member, destination, filter='data')
            now = time.monotonic()
            if now - last > 0.4 or index == len(members) - 1:
                progress.emit('extract', completed=index + 1, entries=len(members), force=True)
                last = now


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def verify_program(program, progress):
    node = program / 'node'
    if not node.is_file() or not (program / 'index.mjs').is_file():
        raise ValueError('The archive does not contain the expected SnowLuma layout')
    if not (program / 'native/snowluma-linux-arm64.so').is_file():
        raise ValueError('The archive does not contain the Linux ARM64 hook library')
    os.chmod(node, 0o755)
    if (program / 'launcher.sh').is_file():
        os.chmod(program / 'launcher.sh', 0o755)
    progress.emit('verify_program', activity='checking', force=True)
    result = run(['/usr/bin/python3', str(DESKTOP / 'guest-compat'), './node', './check-node-version.cjs'],
                 cwd=program, text=True, capture_output=True)
    print(result.stdout.rstrip(), flush=True)
    if result.returncode:
        print(result.stderr.rstrip(), flush=True)
        raise ValueError('The bundled Node.js runtime did not start in this Ubuntu environment')


def install():
    progress = Progress()
    lock = json.loads((SOURCE / 'sources.json').read_text())
    if subprocess.check_output(['dpkg', '--print-architecture'], text=True).strip() != lock['architecture']:
        raise ValueError('SnowLuma installation requires the ARM64 runtime')
    if not (DESKTOP / 'guest-compat').is_file() or not (DESKTOP / 'libguest-compat.so').is_file():
        raise ValueError('Install the Ubuntu desktop extension first')
    swapped = False
    try:
        if archive_matches(lock['asset']):
            print('Verified package already present; skipping download.', flush=True)
            progress.emit('download', downloaded=lock['asset']['bytes'], total=lock['asset']['bytes'], force=True)
        else:
            progress.emit('download', downloaded=0, total=lock['asset']['bytes'], force=True)
            download(lock['asset'], progress)
        progress.emit('verify', force=True)
        verify_archive(lock['asset'])
        progress.emit('extract', force=True)
        extract(ARCHIVE, STAGING, progress)
        verify_program(STAGING, progress)
        # WebUI credentials and account data live inside the program directory;
        # keep them across reinstalls and version switches.
        for name in ('config', 'data', 'logs'):
            source_dir = PROGRAM / name
            if source_dir.is_dir():
                shutil.copytree(source_dir, STAGING / name, dirs_exist_ok=True)
        if PREVIOUS.exists():
            shutil.rmtree(PREVIOUS)
        if PROGRAM.exists():
            PROGRAM.rename(PREVIOUS)
        STAGING.rename(PROGRAM)
        swapped = True
        progress.emit('configure', force=True)
        hook = PROGRAM / lock['hook']['library']
        run(['/usr/bin/python3', str(DESKTOP / 'guest-compat'), 'configure',
             '--library', str(hook), '--symbol', lock['hook']['symbol'],
             '--wait-module', lock['hook']['waitModule']], text=True)
        atomic_json(MARKER, {'schemaVersion': 1, 'version': lock['version'],
            'archiveSHA256': lock['asset']['sha256'], 'library': str(hook),
            'webui': lock['webui'], 'installedAt': int(time.time())})
        if PREVIOUS.exists():
            shutil.rmtree(PREVIOUS)
        ARCHIVE.unlink(missing_ok=True)
        progress.emit('finished', force=True)
    except BaseException:
        shutil.rmtree(STAGING, ignore_errors=True)
        ARCHIVE.unlink(missing_ok=True)
        ARCHIVE.with_suffix('.part').unlink(missing_ok=True)
        if swapped:
            shutil.rmtree(PROGRAM, ignore_errors=True)
            MARKER.unlink(missing_ok=True)
        if not PROGRAM.exists() and PREVIOUS.exists():
            PREVIOUS.rename(PROGRAM)
        raise


def uninstall():
    if PROFILE.is_file():
        try:
            profile = json.loads(PROFILE.read_text())
            library = str(profile.get('library', ''))
        except (OSError, ValueError):
            library = ''
        if library.startswith(str(BASE.resolve())):
            PROFILE.unlink(missing_ok=True)
            print('Optional startup profile removed.', flush=True)
    for path in (STAGING, PREVIOUS, PROGRAM):
        shutil.rmtree(path, ignore_errors=True)
    for path in (ARCHIVE, ARCHIVE.with_suffix('.part'), MARKER):
        path.unlink(missing_ok=True)
    print('SnowLuma program removed; account data under /persistent/snowluma was kept.', flush=True)


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else ''
    if mode == 'install':
        install()
    elif mode == 'uninstall':
        uninstall()
    else:
        raise SystemExit('Usage: install.py install|uninstall')
