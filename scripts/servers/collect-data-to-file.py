import json
import os
import re
import argutil

MIN_VERSION = 8
EXPAND_VERSIONS = []
SOFTWARE_LIST = {
    "paper": True,
    "bukkit": False
}
OUTPUT_FILE = os.path.join("public", "servers.json")
TEMPLATE_FILE = os.path.join("public", "servers-template.json")

# Old "1.x[.y]" or new year-prefixed "YY.x[.y]"
VERSION_PATTERN = re.compile(r"^(\d+)\.(\d+)(?:\.(\d+))?")


def load_json(file_name):
    with open(file_name, "r") as f:
        return json.load(f)


def parse_parent_version(version):
    match = VERSION_PATTERN.match(version)
    if not match:
        return None
    major, minor, _patch = match.groups()
    return f"{major}.{minor}"


def is_supported_version(version):
    match = VERSION_PATTERN.match(version)
    if not match:
        return False
    major = int(match.group(1))
    return major == 1 or major >= 2


def add_to_version_data(version, version_data, server_count):
    parent_version = parse_parent_version(version)
    if parent_version is None:
        # Unknown, record as-is
        version_data[version] = server_count
        return

    if parent_version.startswith("1."):
        try:
            if int(parent_version[2:]) < MIN_VERSION:
                return
        except ValueError:
            # Ignore dumb version
            pass

    if parent_version in EXPAND_VERSIONS:
        # Exempted: record the exact version too
        version_data[version] = server_count

    if parent_version not in version_data:
        version_data[parent_version] = server_count
    else:
        version_data[parent_version] = version_data[parent_version] + server_count


def handle(append_to, file_name, full=False):
    processed_data = {}
    raw_data_by_date = load_json(os.path.join("data", file_name))
    append_to.append({"date": file_name.replace(".json", ""), "data": processed_data})

    for software in SOFTWARE_LIST:
        if software not in raw_data_by_date:
            # Tracked software didn't exist yet
            processed_data[software] = {}
            continue

        version_data = {}
        processed_data[software] = version_data
        software_data = raw_data_by_date[software]
        for software_version_data in software_data["minecraft_version"]:
            if isinstance(software_version_data, str):
                # Oh well
                continue

            version = software_version_data["name"]
            server_count = software_version_data["y"]
            if full:
                version_data[version] = server_count
                continue

            # Ignore the custom garbage; accept old (1.x) and new (year-first) formats
            if not is_supported_version(version):
                continue

            try:
                add_to_version_data(version, version_data, server_count)
            except ValueError:
                version_data[version] = server_count

    # TODO This is kind of dumb and can break results due to how Paper vs. the global Bukkit stats are counted
    #  bStats unfortunately doesn't have proper platform distinction here
    booketData = processed_data["bukkit"]
    for software, remove_from_bukkit in SOFTWARE_LIST.items():
        if not remove_from_bukkit:
            continue

        for version, y in processed_data[software].items():
            if version in booketData:
                booketData[version] = booketData[version] - y


if os.path.isfile(OUTPUT_FILE):
    servers = load_json(OUTPUT_FILE)
else:
    servers = load_json(TEMPLATE_FILE)

if argutil.hasArg("date"):
    # Append the single data file
    handle(servers["data"], argutil.getArg("date") + ".json")
else:
    # List entire data directory
    data = []
    servers["data"] = data
    for file_name in sorted(os.listdir("data")):
        if file_name.endswith(".json"):
            handle(data, file_name)

with open(OUTPUT_FILE, "w") as file:
    json.dump(servers, file, indent=2)