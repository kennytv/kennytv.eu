#!/usr/bin/env python3
"""Generate packet wire-format data for a Minecraft version.

Downloads the (unobfuscated) vanilla server jar, unpacks the bundler, compiles the
Java extractor against the game jar, runs it, and writes the result to
public/packet-data/<version>.json (+ index.json).

Usage:
    python scripts/packets/generate.py <version|release|snapshot> [--pretty]

Requires a JDK matching the game's Java requirement (currently 25) on PATH.
"""
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
REPO_ROOT = SCRIPT_DIR.parent.parent
OUTPUT_DIR = REPO_ROOT / 'public' / 'packet-data'
WORK_DIR = SCRIPT_DIR / 'work'
EXTRACTOR_SRC = SCRIPT_DIR / 'extractor' / 'src'

MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'


def fetch_json(url: str):
    with urllib.request.urlopen(url) as stream:
        return json.load(stream)


def download(url: str, target: Path):
    target.parent.mkdir(parents=True, exist_ok=True)
    print(f'Downloading {url} -> {target}', flush=True)
    urllib.request.urlretrieve(url, target)


def resolve_version(requested: str) -> dict:
    manifest = fetch_json(MANIFEST_URL)
    if requested in ('release', 'snapshot'):
        requested = manifest['latest'][requested]
        print(f'Resolved to {requested}', flush=True)
    for entry in manifest['versions']:
        if entry['id'] == requested:
            return fetch_json(entry['url'])
    sys.exit(f'Version {requested} not found in manifest')


def unpack_bundler(bundler_jar: Path, dest: Path) -> Path:
    """Extract the embedded game jar and libraries from a bundler server jar."""
    libs_dir = dest / 'libraries'
    libs_dir.mkdir(parents=True, exist_ok=True)
    game_jar = None
    with zipfile.ZipFile(bundler_jar) as z:
        for name in z.namelist():
            if not name.endswith('.jar'):
                continue
            if name.startswith('META-INF/versions/'):
                game_jar = dest / Path(name).name
                target = game_jar
            elif name.startswith('META-INF/libraries/'):
                target = libs_dir / Path(name).name
            else:
                continue
            if not target.exists():
                with z.open(name) as src, open(target, 'wb') as out:
                    shutil.copyfileobj(src, out)
    if game_jar is None:
        sys.exit('No game jar found inside the bundler — very old version?')
    return game_jar


def run_extractor(game_jar: Path, libs_dir: Path, version: str, output: Path, pretty: bool):
    classes_dir = WORK_DIR / 'classes'
    if classes_dir.exists():
        shutil.rmtree(classes_dir)
    sep = ';' if os.name == 'nt' else ':'
    cp = f'{game_jar}{sep}{libs_dir}/*'
    sources = [str(p) for p in EXTRACTOR_SRC.rglob('*.java')]

    print('Compiling extractor...', flush=True)
    subprocess.run(['javac', '-d', str(classes_dir), '-cp', cp, *sources], check=True)

    print('Running extractor...', flush=True)
    args = ['java', '-cp', f'{classes_dir}{sep}{cp}', 'PacketExtractor',
            str(game_jar), version, str(output.resolve())]
    if pretty:
        args.append('--pretty')
    # cwd = work dir so the game's log files land somewhere gitignored
    subprocess.run(args, check=True, cwd=WORK_DIR)


def version_sort_key(v: str):
    parts = []
    for chunk in v.replace('-', '.').split('.'):
        parts.append(int(chunk) if chunk.isdigit() else -1)
    return parts


def update_index(version: str):
    index_path = OUTPUT_DIR / 'index.json'
    index = {'versions': []}
    if index_path.exists():
        with open(index_path) as f:
            index = json.load(f)
    if version not in index['versions']:
        index['versions'].append(version)
        index['versions'].sort(key=version_sort_key)
    with open(index_path, 'w') as f:
        json.dump(index, f, separators=(',', ':'))
    print(f'index.json now lists {len(index["versions"])} versions', flush=True)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    requested = sys.argv[1]
    pretty = '--pretty' in sys.argv

    meta = resolve_version(requested)
    version = meta['id']
    java_major = meta.get('javaVersion', {}).get('majorVersion')
    if java_major:
        result = subprocess.run(['javac', '-version'], capture_output=True, text=True)
        installed = int(result.stdout.split()[1].split('.')[0]) if result.stdout else 0
        if installed < java_major:
            sys.exit(f'{version} needs JDK {java_major}, found javac {result.stdout.strip()}')

    version_dir = WORK_DIR / version
    bundler_jar = version_dir / 'server-bundler.jar'
    if not bundler_jar.exists():
        download(meta['downloads']['server']['url'], bundler_jar)

    game_jar = unpack_bundler(bundler_jar, version_dir)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUTPUT_DIR / f'{version}.json'
    run_extractor(game_jar, version_dir / 'libraries', version, output, pretty)
    update_index(version)
    print(f'Done: {output}', flush=True)


if __name__ == '__main__':
    main()
