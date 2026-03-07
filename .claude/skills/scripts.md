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

- `packet_data.py` — `MinecraftPacketAnalyzer` class: parses Java source for packet definitions (`implements Packet<>`), extracts fields from `StreamCodec.composite()`, `Packet.codec()` write methods, and `StreamCodec.unit()`. Parses protocol registration files for packet indices. Detects custom codec types (composite records, dispatch interfaces). See `packet-data.md` for full details.
- `all_packet_data.py` — Iterates release commits in a Minecraft source repo (skips snapshots), runs the analyzer at each, produces per-version JSON files in `public/packet-data/` with deduplication.
- `single_packet_data.py` — Adds/updates a single version: writes `public/packet-data/<version>.json` and updates `public/packet-data/index.json`.

## GitHub Actions (`.github/workflows/`)

- `deploy.yml` — Builds with Bun and deploys static site to GitHub Pages on push to `main`.
- `update-data.yml` — Daily cron (18:00 UTC): runs `main.py` + `collect-data-to-file.py`, commits updated `servers.json`.
