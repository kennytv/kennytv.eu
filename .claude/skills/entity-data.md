# Entity Data

Minecraft entity data field browser and cross-version diff tool at `/entity-data`. Parses decompiled Java source to extract `EntityDataAccessor` fields (synched entity metadata).

## Components (`src/app/entity-data/_components/`)

## Data Processing (`src/app/entity-data/_lib/`)

## Data

- `public/entity-data.json` — Multi-version entity data JSON consumed at runtime
- `docs/entity_data.md` — Generated Markdown reference document

## Data Generation

See `scripts.md` for the Python scripts (`scripts/entities/`) that parse Minecraft source and produce the JSON data.
