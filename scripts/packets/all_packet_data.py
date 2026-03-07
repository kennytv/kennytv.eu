import json
import os
import subprocess
import packet_data

OUTPUT_DIR = os.path.join('public', 'packet-data')
INDEX_PATH = os.path.join(OUTPUT_DIR, 'index.json')
SOURCE_DIR = os.path.expanduser(os.path.join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft"))


def get_commit_list() -> list:
    # Get all commit hashes and titles from oldest to newest
    result = subprocess.run(
        ['git', 'log', '--reverse', '--pretty=format:%H %s'],
        stdout=subprocess.PIPE,
        text=True
    )

    # Parse each commit into (hash, title)
    commits = result.stdout.splitlines()
    commit_list = [(line.split(' ', 1)[0], line.split(' ', 1)[1]) for line in commits]

    return commit_list


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Go through all release commits in reverse
    commits = get_commit_list()
    all_versions = []
    last_json = None

    for commit_hash, commit_title in commits:
        if 'w' in commit_title or '-' in commit_title:
            # Skip snapshots
            continue

        print(f'Processing {commit_title}...', flush=True)
        subprocess.run(['git', 'checkout', commit_hash], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        analyzer = packet_data.analyze(SOURCE_DIR)
        version_json = analyzer.generate_json()

        # Skip if identical to previous version
        if version_json == last_json:
            print(f'  Skipped (identical to previous version)', flush=True)
            continue

        # Write individual version file
        version_path = os.path.join(OUTPUT_DIR, f'{commit_title}.json')
        with open(version_path, 'w') as f:
            json.dump(version_json, f, separators=(',', ':'))

        all_versions.append(commit_title)
        last_json = version_json

    # Write index file
    index = {"versions": all_versions}
    with open(INDEX_PATH, 'w') as f:
        json.dump(index, f, separators=(',', ':'))

    print(f'\nWrote {len(all_versions)} versions to {OUTPUT_DIR}/', flush=True)


if __name__ == "__main__":
    main()
