# Scripts

### Server Data (`scripts/servers/`)

- `main.py` — Fetches daily Minecraft server stats from bStats API for Paper, Bukkit, Purpur, Folia. Saves to `data/YYYY-MM-DD.json`. Flag `-n` for single run, otherwise runs as scheduled daemon.
- `collect-data-to-file.py` — Aggregates raw daily files into `public/servers.json`. Groups minor versions (1.x.y → 1.x). Subtracts Paper counts from Bukkit totals to avoid double-counting.
- `argutil.py` — Minimal CLI argument parser utility.

### Entity Data (`scripts/entities/`)

- `entity_data.py` — `MinecraftEntityAnalyzer` class: parses Java source files for `EntityDataAccessor` definitions via regex, calculates field indices through inheritance chains, generates Markdown and JSON output.
- `all_entity_data.py` — Iterates release commits in a Minecraft source repo (skips snapshots), runs the analyzer at each, produces multi-version `entity-data.json` with deduplication.
- `single_entity_data.py` — Adds/updates a single version in `public/entity-data.json`.

### Packet Data (`scripts/packets/`)

- `generate.py` — Full pipeline for one version: downloads the unobfuscated vanilla server jar, unpacks the bundler, compiles `extractor/src/*.java` against the game jar (JDK 25+), runs it, writes `public/packet-data/<version>.json` + `index.json`. Usage: `python scripts/packets/generate.py <version|release|snapshot>`.
- `extractor/src/` — Java extractor that runs real game code to produce exact wire formats. See `packet-data.md` for full details.

## GitHub Actions (`.github/workflows/`)

- `deploy.yml` — Builds with Bun and deploys static site to GitHub Pages on push to `main`.
- `update-data.yml` — Daily cron (18:00 UTC): runs `main.py` + `collect-data-to-file.py`, commits updated `servers.json`.
