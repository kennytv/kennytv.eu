# Packet Data

Minecraft packet wire-format browser and cross-version diff at `/packet-data`. Data is extracted by **running real game code** against the unobfuscated vanilla server jar — no decompiled source, no regex parsing.

## Regenerating data

```bash
python scripts/packets/generate.py <version|release|snapshot>   # add --pretty for readable JSON
```

Downloads the server jar from piston-meta (cached in `scripts/packets/work/`, gitignored), unzips the bundler's embedded game jar + libraries, compiles the extractor against them (JDK 25+ on PATH), runs it, writes `public/packet-data/<version>.json` and updates `index.json`. Quality check: the final stderr line — **opaque and dangling-ref counts must be 0** (e.g. `260 packets, 212 shared types, 0 opaque, 0 dangling refs`), with no `[patch]` or `FAILED` lines.

Only unobfuscated-jar versions work (26.x era). Pre-obfuscation-removal versions are not supported.

## Extractor (`scripts/packets/extractor/src/`)

Six files, compiled per-run against the game jar (JDK 25+: uses `java.lang.classfile`). Version-sensitive APIs (registry layer names, newer `ByteBufCodecs` factories) are resolved reflectively so older 26.x jars still compile.

- `PacketExtractor.java` — main. Bootstraps the game, loads full registries (data-driven via vanilla datapack) and binds default item components (`DATA_COMPONENT_INITIALIZERS`, needed to decode ItemStacks). Scans the jar for `PacketType` constants (→ packet classes) and `StreamCodec` constants (→ labels like `ItemStack.STREAM_CODEC`; anonymous holders label as their outer class). Packet ids come from the unbound protocol templates' `CodecEntry` lists. Final pass unifies traced `Enum` reads into `enum` nodes and counts dangling refs. `--debug=<PacketClassSimpleName>` dumps one packet's trace.
- `ShapeRegistry.java` — learns combinator anonymous-class names from samples. Factory kinds (`string`, `byteArray`, `fixedCollection`, `fixedBitSet`, …) are registered **before** the `ByteBufCodecs` constants, or constants like `STRING_UTF8` would hide their limits.
- `CodecWalker.java` — interprets live `StreamCodec`s: composite (record names; non-records/mismatched records name sub-codecs by replay, see below), optional/list/fixed-size list (`x`)/map/either/registry/holder/holderSet/idMapper (probed incl. sparse ids → `NAME=id`), lengthPrefixed, mapped codecs (`retype`: produced class becomes `java`, String→`Identifier`, Long→`Long (Instant)`, …; int-packed enum sets get bit notes), dispatch (single-body dispatches collapse to key + value; null-codec keys say "never sent"), `CustomPacketPayload.codec` (channel dispatch incl. `minecraft:brand`), transparent wrappers (`validatedStreamCodec`). `specialCodec()` hand-models: ItemStack (+ untrusted/delimited), DataComponentPatch, TypedDataComponent, set_entity_data (dispatch over all serializers), set_equipment, section_blocks_update, LpVec3, MapPatch, MessageSignature.Packed, the commands graph entry (argument parser properties traced per registered parser).
- `DecodeTracer.java` — the fallback for hand-written `read()` codecs: decodes against `FabricatingByteBuf` and rebuilds structure from stack traces. **Call-site splicing**: when a game-class reader hands the buffer to a codec (`SoundEvent.STREAM_CODEC.decode(input)`, `readNullable(X.STREAM_CODEC)`, `ByteBufCodecs.registry(...)`), `CallSiteCodecs` resolves that codec from bytecode and the walker's exact node replaces the raw reads. Branch exploration: enum switches (labels via enum field or enum accessor like `Operation#getType`), byte switches (keys from the class's int constant family — `METHOD_ADD=0`, or a power-of-two family / structural two-bit mask → `flags`), EnumSet sections (with the constant list), registry dispatches. A container helper called again from the same call site (several `readList` calls in one multi-line constructor call) is split by its recurring head read. Hand patches (flags notes in hex, DisplayInfo flags/background, move-entity VecDelta, custom_query_answer, pre-26.3 commands, structure block, …) report misses loudly; a trace that only decodes with zero counts while containing lists prints `[tracer] WARNING` (element shapes unknown).
- `CallSiteCodecs.java` — `java.lang.classfile` helpers: abstract stack interpretation to find the codec consumed by an invoke at a given line (survives multi-line calls), composite decode-call lines, and LocalVariableTable lookups (the local a read is stored into).
- `PerturbNaming.java` — names by **observation**: replays a trace with one read answered differently and diffs the decoded object graphs. The single field that changed names the read (wire order ≠ declaration order, e.g. set_experience); id reads are probed for their constants (`STATUS=1`), string/list/byte-array limits are read off the reader's rejection of an oversized length, "0 = absent" encodings get notes, flipped Booleans reveal conditional fields (`only when atEntity is true`) and the second Either branch, and a VarInt whose replays with 0/2 drop/duplicate the following reads is a hand-written loop count (→ list). Fallback: the LocalVariableTable name of the local a read is stored into.
- `io/netty/buffer/FabricatingByteBuf.java` — synthesizes bytes on demand, records a stack per read.

Maintenance model: unknown combinators degrade to tracing (`[walker] tracing unknown shape` is informational). API renames fail the compile loudly. Hand models/patches must be revalidated when Mojang changes those encodings. Quality gate: the summary line must read `0 opaque, 0 dangling refs` and no `[patch]`/`FAILED` lines may appear. For a deep check, render the JSON to text and audit packets against decompiled sources (`../MCSources`, git history has every version) — see `scripts/packets/REMAINING.md` for open items.

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

- Nested branch explorations don't compose: a switch inside a traced switch body shows the sampled path only. Byte switches probe 0..4; values beyond need a constant family in the packet class.
- Traced dispatch/EnumSet variant bodies are named best-effort; ambiguous ones stay unnamed rather than guessing. Open items live in `scripts/packets/REMAINING.md`.
