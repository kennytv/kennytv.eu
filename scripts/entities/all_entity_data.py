import json
import os
import subprocess
import entity_data


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
    # Go through all release commits in reverse
    commits = get_commit_list()
    all_versions = []
    all_data = {}
    last_json = None

    for commit_hash, commit_title in commits:
        if 'w' in commit_title or '-' in commit_title:
            # Skip snapshots
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

        all_versions.append(commit_title)
        all_data[commit_title] = version_json
        last_json = version_json

    # Write single output file
    output = {
        "versions": all_versions,
        "data": all_data,
    }

    with open('entity-data.json', 'w') as f:
        json.dump(output, f, separators=(',', ':'))

    print(f'\nWrote {len(all_versions)} versions to entity-data.json', flush=True)


if __name__ == "__main__":
    main()
