# Entity Data

Minecraft entity data field browser and cross-version diff tool at `/entity-data`. Parses decompiled Java source to extract `EntityDataAccessor` fields (synched entity metadata).

## Components (`src/app/entity-data/_components/`)

## Data Processing (`src/app/entity-data/_lib/`)

## Data

- `public/entity-data.json` — Multi-version entity data JSON consumed at runtime
- `docs/entity_data.md` — Generated Markdown reference document

## Data Generation

See `scripts.md` for the Python scripts (`scripts/entities/`) that parse Minecraft source and produce the JSON data.

Verification (26.x, unobfuscated jars): a throwaway Java oracle that bootstraps the server jar, loads every `Entity` subclass and reads each static `EntityDataAccessor`'s runtime `id()`/serializer gives ground truth for indices; compared against the analyzer for 26.2 and 26.3 it matched every field (only differences: the deliberately excluded side-specific ServerPlayer and GameTest's anonymous mock players).
