# Packet Data

Minecraft packet wire-format browser and cross-version diff at `/packet-data`. Data is extracted by **running real game code** against the unobfuscated vanilla server jar — no decompiled source, no regex parsing.

## Regenerating data

```bash
python scripts/packets/generate.py <version|release|snapshot>   # add --pretty for readable JSON
```

Downloads the server jar from piston-meta (cached in `scripts/packets/work/`, gitignored), unzips the bundler's embedded game jar + libraries, compiles the extractor against them (JDK 25+ on PATH), runs it, writes `public/packet-data/<version>.json` and updates `index.json`. Quality check: the final stderr line — **opaque count must be 0** (e.g. `256 packets, 164 shared types, 0 opaque`).

Only unobfuscated-jar versions work (26.x era). Pre-obfuscation-removal versions are not supported.

## Extractor (`scripts/packets/extractor/src/`)

Four files, compiled per-run against the game jar:

- `PacketExtractor.java` — main. Bootstraps the game, loads full registries (incl. data-driven via vanilla datapack), scans the jar for `PacketType` constants (→ packet classes via generic field signatures) and `StreamCodec` constants (→ identity labels like `ItemStack.STREAM_CODEC`). Packet ids/protocols come from reflecting the unbound protocol templates' captured `CodecEntry` lists (`val$codecs`) — same ground truth as vanilla's own packet report. Emits JSON via Gson. `--debug=<PacketClassSimpleName>` instead of an output path traces one packet with every policy and dumps events + stacks.
- `ShapeRegistry.java` — learns the anonymous-class names of codec combinators by instantiating samples at startup, so codecs and stack frames are identified by exact class, not field-name heuristics.
- `CodecWalker.java` — recursively interprets live `StreamCodec` objects: composite (field names/types from record components or constructor parameters — parameter names ship in the jar), optional/list/map/either/registry/holder/holderSet/idMapper (probed for enum values)/lengthPrefixed/parsedCodec. Dispatch codecs enumerate every registry/id key into `variants` (identical bodies merged). Labeled non-trivial codecs become entries in the shared `types` table, referenced via `ref` nodes. `specialCodec()` hand-models formats tracing can't express: ItemStack, DataComponentPatch (all component types enumerated via `type.streamCodec()`), set_entity_data / set_equipment / section_blocks_update (sentinel/bit-packed loops).
- `DecodeTracer.java` — the universal fallback for `of`/`ofMember` (manual `write()/read()`) codecs: decodes against a `FabricatingByteBuf` (in `io/netty/buffer/`, extends `UnpooledHeapByteBuf`) that synthesizes bytes on demand and records a stack trace per read. Frames give nesting (constructor/reader frames become groups, known combinator frames become List/Map/Optional containers) and wire labels (`readVarInt` → VarInt etc., line numbers separate same-type reads). Fabrication policy: varints 1, booleans decay true→false per call site, NBT planted as `{text:"a"}` (falls back to empty compound), unique 2-char strings (field names recovered by matching markers in the decoded object graph), valid RSA key for `readPublicKey`, sentinel-loop breaker (3rd same-site byte → 0xFF). Branch exploration re-decodes with scripted values and diffs unit lists into prefix + variants + common tail: enums (`readEnum` ordinal), plain byte switches (0–4, e.g. team/objective `method`), EnumSet bits (player_info actions), registry-dispatch keys (particles; labeled via reverse registry lookup — must use `getResourceKey`, `getKey` answers defaults). Field naming tries: hierarchy fields positional → bucket queues (equal counts) → constructor params → order-preserving partial match, all guarded by wire↔type affinity vetoes (misnaming is worse than unnamed).

Maintenance model: unknown new combinators degrade to tracing automatically (`[walker] tracing unknown shape` on stderr is informational). API renames in a new version fail the compile loudly — fix the few lines. The hand-modeled specials in `specialCodec()` must be revalidated if Mojang changes those encodings.

## JSON schema

`{version, protocolVersion, protocols: {handshake|status|login|configuration|play: {clientbound|serverbound: [{index, id, class, body}]}}, types: {name: node}}`

Recursive node: `kind` ∈ `value` (leaf: `wire`), `container`/`traced` (`fields`), `group` (`context` + `fields`, from traced frames), `list`/`optional`/`prefixed` (`elem`/`inner`, counts/flags implied), `map` (`key`+`value`), `either`, `registry`/`holder`/`holderSet` (`registry` id; holder may have `direct`), `enum` (`values`), `dispatch` (`variants: [{key, body}]`, keys comma-merged when bodies match), `ref` (→ `types`), `unit` (empty), `opaque`. Optional keys anywhere: `name`, `java`, `note`, `limit`, `x` (repetition of merged fixed-size reads), `link`/`linkText` (related page), `partial`.

## Frontend (`src/app/packet-data/`)

- `_lib/packetDataUtils.ts` — types, recursive search, `flattenNode` + LCS `diffLines` for version diffing, anchors.
- `_components/NodeTree.tsx` — recursive renderer; simple subtrees render inline (`Optional<UUID>`, `Map<K, V>`), complex ones as indented blocks; collapsible variants.
- `PacketCard` / `TypesSection` — cards with hover `#` anchor links (hash navigation, browser back works).
- `TypePanel.tsx` — side panel opened by type-ref clicks; breadcrumb trail for nested types.
- `PacketBrowseView` / `PacketDiffView` / `PacketDataClient` — browse/search, line-level tree diff between versions, index/fetch plumbing.

## Known limitations

- Byte-flag conditionals inside traced packets show the maximal path, not per-flag variants (e.g. `add_entity.movement` LpVec3 shows only its header byte; set_objective's fields show under method "0, 2" but its nested number-format dispatch is a sampled path — nested explorations don't compose).
- Traced dispatch/EnumSet variant bodies are named best-effort; ambiguous ones stay unnamed rather than guessing.
