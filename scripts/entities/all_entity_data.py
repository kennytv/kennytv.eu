import json
import os
import subprocess
import entity_data

OUTPUT_DIR = os.path.join('public', 'entity-data')


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
        if 'w' in commit_title or '-' in commit_title or '.' not in commit_title:
            # Skip snapshots/invalid versions
            continue

        print(f'Processing {commit_title}...', flush=True)
        subprocess.run(['git', 'checkout', commit_hash], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        analyzer = entity_data.analyze(
            os.path.expanduser(os.path.join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft", "world", "entity"))
        )
        version_json = analyzer.generate_json()

        # Skip if identical to previous version
        if version_json == last_json:
            print(f'  Skipped (identical to previous version)', flush=True)
            continue

        # Write per-version file
        with open(os.path.join(OUTPUT_DIR, f'{commit_title}.json'), 'w') as f:
            json.dump(version_json, f, indent=2)

        all_versions.append(commit_title)
        last_json = version_json

    # Write versions index
    with open(os.path.join(OUTPUT_DIR, 'versions.json'), 'w') as f:
        json.dump(all_versions, f, indent=2)

    print(f'\nWrote {len(all_versions)} versions to {OUTPUT_DIR}/', flush=True)


if __name__ == "__main__":
    main()
