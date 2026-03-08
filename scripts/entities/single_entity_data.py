import json
import os
import sys
import entity_data

OUTPUT_DIR = os.path.join('public', 'entity-data')
VERSIONS_PATH = os.path.join(OUTPUT_DIR, 'versions.json')
SOURCE_DIR = os.path.expanduser(os.path.join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft", "world", "entity"))


def load_versions() -> list:
    """Load the existing version list, or return an empty list."""
    if os.path.exists(VERSIONS_PATH):
        with open(VERSIONS_PATH, 'r') as f:
            return json.load(f)
    return []


def load_version_data(version: str) -> dict | None:
    """Load the data for a specific version, or return None."""
    path = os.path.join(OUTPUT_DIR, f'{version}.json')
    if os.path.exists(path):
        with open(path, 'r') as f:
            return json.load(f)
    return None


def save_versions(versions: list):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(VERSIONS_PATH, 'w') as f:
        json.dump(versions, f, indent=2)


def save_version_data(version: str, data: dict):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(os.path.join(OUTPUT_DIR, f'{version}.json'), 'w') as f:
        json.dump(data, f, indent=2)


def main():
    if len(sys.argv) < 2:
        print('Usage: python single_entity_data.py <version>', file=sys.stderr)
        print('Example: python single_entity_data.py 1.21.5', file=sys.stderr)
        sys.exit(1)

    version = sys.argv[1]

    # Analyze the current source tree
    print(f'Analyzing entity data for {version}...', flush=True)
    analyzer = entity_data.analyze(SOURCE_DIR)
    version_json = analyzer.generate_json()

    # Check for identical existing data
    existing_data = load_version_data(version)
    if existing_data == version_json:
        print(f'{version} already exists with identical data, nothing to do.', flush=True)
        return

    # Write version data file
    save_version_data(version, version_json)

    # Update version list
    versions = load_versions()
    if version not in versions:
        # Insert in sorted position
        inserted = False
        for i, existing in enumerate(versions):
            if version_compare(version, existing) < 0:
                versions.insert(i, version)
                inserted = True
                break
        if not inserted:
            versions.append(version)
        save_versions(versions)
        print(f'Added {version}', flush=True)
    else:
        print(f'Updated {version}', flush=True)

    print(f'Total versions: {len(versions)}', flush=True)


def version_compare(a: str, b: str) -> int:
    """Compare two version strings numerically (e.g. 1.9 < 1.10 < 1.21.4).

    Non-numeric segments sort after numeric ones.
    """
    def parts(v: str):
        segments = []
        for x in v.split('.'):
            try:
                segments.append((0, int(x), ''))
            except ValueError:
                segments.append((1, 0, x))
        return segments

    pa, pb = parts(a), parts(b)
    for sa, sb in zip(pa, pb):
        if sa != sb:
            return -1 if sa < sb else 1
    return len(pa) - len(pb)


if __name__ == "__main__":
    main()
