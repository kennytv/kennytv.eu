import json
import os
import sys
import packet_data

OUTPUT_DIR = os.path.join('public', 'packet-data')
INDEX_PATH = os.path.join(OUTPUT_DIR, 'index.json')
SOURCE_DIR = os.path.expanduser(os.path.join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft"))


def load_index() -> dict:
    """Load the existing index file, or return an empty structure."""
    if os.path.exists(INDEX_PATH):
        with open(INDEX_PATH, 'r') as f:
            return json.load(f)
    return {"versions": []}


def save_index(index: dict):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(INDEX_PATH, 'w') as f:
        json.dump(index, f, separators=(',', ':'))


def save_version(version: str, data: dict):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    path = os.path.join(OUTPUT_DIR, f'{version}.json')
    with open(path, 'w') as f:
        json.dump(data, f, separators=(',', ':'))


def version_compare(a: str, b: str) -> int:
    """Compare two version strings numerically (e.g. 1.9 < 1.10 < 1.21.4)."""
    def parts(v: str):
        return [int(x) for x in v.split('.')]
    pa, pb = parts(a), parts(b)
    for x, y in zip(pa, pb):
        if x != y:
            return x - y
    return len(pa) - len(pb)


def main():
    if len(sys.argv) < 2:
        print('Usage: python single_packet_data.py <version>', file=sys.stderr)
        print('Example: python single_packet_data.py 1.21.5', file=sys.stderr)
        sys.exit(1)

    version = sys.argv[1]

    # Analyze the current source tree
    print(f'Analyzing packet data for {version}...', flush=True)
    analyzer = packet_data.analyze(SOURCE_DIR)
    version_json = analyzer.generate_json()

    # Check if data is identical to existing
    version_path = os.path.join(OUTPUT_DIR, f'{version}.json')
    if os.path.exists(version_path):
        with open(version_path, 'r') as f:
            existing = json.load(f)
        if existing == version_json:
            print(f'{version} already exists with identical data, nothing to do.', flush=True)
            return

    # Save version data
    save_version(version, version_json)
    print(f'Wrote {version}.json', flush=True)

    # Update index
    index = load_index()

    if version not in index["versions"]:
        # Insert in sorted position
        inserted = False
        for i, existing in enumerate(index["versions"]):
            if version_compare(version, existing) < 0:
                index["versions"].insert(i, version)
                inserted = True
                break
        if not inserted:
            index["versions"].append(version)

    save_index(index)
    print(f'Updated index.json — total versions: {len(index["versions"])}', flush=True)


if __name__ == "__main__":
    main()
