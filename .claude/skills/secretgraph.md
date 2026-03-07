# Secretgraph

Minecraft server software usage statistics dashboard at `/secretgraph`. Visualizes historical bStats data for Paper, Bukkit/Spigot, Purpur, and Folia.

## Components (`src/app/secretgraph/_components/`)

## Data Processing (`src/app/secretgraph/_lib/`)

## Data

- `public/servers.json` — Processed server statistics JSON consumed at runtime
- `data/*.json` — Raw daily bStats snapshots (gitignored, local only)

## Types

Defined in `src/lib/types.ts`: `ServerData`, `PlatformInfo`, `DataEntry`, `ChartDataPoint`

## Data Pipeline

See `scripts.md` for the Python scripts (`scripts/servers/`) and GitHub Action (`update-data.yml`) that collect and aggregate bStats data daily.
