import json
import os
import sys
import entity_data

OUTPUT_PATH = os.path.join('public', 'entity-data.json')
SOURCE_DIR = os.path.expanduser(os.path.join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft", "world", "entity"))


def load_existing() -> dict:
    """Load the existing JSON file, or return an empty structure."""
    if os.path.exists(OUTPUT_PATH):
        with open(OUTPUT_PATH, 'r') as f:
            return json.load(f)
    return {"versions": [], "data": {}}


def save(output: dict):
    with open(OUTPUT_PATH, 'w') as f:
        json.dump(output, f, separators=(',', ':'))


def main():
    if len(sys.argv) < 2:
        print('Usage: python all_entity_data.py <version>', file=sys.stderr)
        print('Example: python all_entity_data.py 1.21.5', file=sys.stderr)
        sys.exit(1)

    version = sys.argv[1]

    # Analyze the current source tree
    print(f'Analyzing entity data for {version}...', flush=True)
    analyzer = entity_data.analyze(SOURCE_DIR)
    version_json = analyzer.generate_json()

    # Load existing data and add/update
    output = load_existing()

    if version in output["data"] and output["data"][version] == version_json:
        print(f'{version} already exists with identical data, nothing to do.', flush=True)
        return

    if version in output["versions"]:
        # Update existing version in place
        output["data"][version] = version_json
        print(f'Updated {version} in {OUTPUT_PATH}', flush=True)
    else:
        # Insert in sorted position
        inserted = False
        for i, existing in enumerate(output["versions"]):
            if version_compare(version, existing) < 0:
                output["versions"].insert(i, version)
                inserted = True
                break
        if not inserted:
            output["versions"].append(version)

        output["data"][version] = version_json
        print(f'Added {version} to {OUTPUT_PATH}', flush=True)

    save(output)
    print(f'Total versions: {len(output["versions"])}', flush=True)


def version_compare(a: str, b: str) -> int:
    """Compare two version strings numerically (e.g. 1.9 < 1.10 < 1.21.4)."""
    def parts(v: str):
        return [int(x) for x in v.split('.')]
    pa, pb = parts(a), parts(b)
    for x, y in zip(pa, pb):
        if x != y:
            return x - y
    return len(pa) - len(pb)


if __name__ == "__main__":
    main()
