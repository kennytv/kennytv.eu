# Packet Data

Minecraft packet definition browser and cross-version diff tool at `/packet-data`. Parses decompiled Java source to extract packet definitions, fields, protocol registrations, and custom codec types.

## Components (`src/app/packet-data/_components/`)

- `PacketDataClient.tsx` — Top-level client component. Fetches `index.json`, caches per-version data, switches browse/diff mode.
- `PacketBrowseView.tsx` — Browse mode. Version selector, search, packets grouped by protocol then direction (Clientbound/Serverbound).
- `PacketDiffView.tsx` — Diff mode. Two version selectors, computes and displays packet diffs grouped by protocol/direction. "Hide index-only changes" toggle.
- `PacketTable.tsx` — Single packet card. Shows name, hex index badge, field table. Type column links to base types section for custom codec types.
- `PacketDiffCard.tsx` — Diff result card. Status badge (added/removed/changed), field-level diff with type change visualization.
- `TypesSection.tsx` — Base types reference at bottom. Shows custom codec types with field tables. Dispatch types shown as "Polymorphic type (dispatch codec)".
- `PacketSearch.tsx` — Search input with result count badge.
- `VersionSelector.tsx` — Version dropdown.

## Data Processing (`src/app/packet-data/_lib/`)

- `packetDataUtils.ts` — Types (`PacketField`, `PacketInfo`, `CustomType`, `VersionPacketData`, `PacketDataIndex`), constants (`PROTOCOL_ORDER`, `DIRECTION_LABELS`, `PROTOCOL_LABELS`), search (`filterPackets`), diff computation (`computePacketDiff`, `diffPacketFields`, `filterPacketDiffs`), type reference helpers (`getReferencedType`).

## Data

- `public/packet-data/index.json` — `{ "versions": ["1.14.4", ...] }` listing all available versions
- `public/packet-data/<version>.json` — Per-version packet data (one file per version, unlike entity data which is all-in-one)

### Per-version JSON schema

```json
{
  "packets": {
    "PLAY": {
      "CLIENTBOUND": [
        { "name": "ClientboundAddEntityPacket", "index": 0, "fields": [
          { "name": "id", "type": "VarInt" },
          { "name": "uuid", "type": "UUID" }
        ]}
      ],
      "SERVERBOUND": [...]
    },
    "CONFIGURATION": { ... },
    "LOGIN": { ... },
    "STATUS": { ... },
    "HANDSHAKE": { ... }
  },
  "types": {
    "Entry": { "fields": [{ "name": "itemId", "type": "VarInt" }] },
    "RecipeDisplay": { "fields": [] }
  }
}
```

Fields may have an optional `"limit"` key (integer) for collection size limits. Types with empty `"fields"` are polymorphic dispatch codecs.

## Data Generation (`scripts/packets/`)

See `scripts.md` for the Python scripts that parse Minecraft source and produce the JSON data.

### Packet Analyzer (`packet_data.py`)

`MinecraftPacketAnalyzer` class with three codec parsing strategies:

1. **`StreamCodec.composite(...)`** — Declarative field pairs `(codec, getter)`. Parsed via `_parse_composite_args` using `_split_top_level` for balanced delimiter splitting. Handles chained `.apply()` wrappers (optional, list, collection).
2. **`Packet.codec(Writer::write, Reader::new)`** — Manual read/write methods. Parsed via `_parse_write_method` which scans `write()` body for `output.writeVarInt(this.x)` patterns, `SomeCodec.encode()` calls, and `ByteBufCodecs.registry().encode()` calls.
3. **`StreamCodec.unit(INSTANCE)`** — Empty packets (no fields).
4. **`.map(Class::new, Class::getter)`** — Single-field wrapper codec pattern.

Type name cleaning (`_clean_type_name`):
- `ByteBufCodecs.VAR_INT` → `VarInt`, `ByteBufCodecs.STRING_UTF8` → `String`, etc.
- `SomeType.STREAM_CODEC` → `SomeType`
- `.apply(ByteBufCodecs::optional)` → `Optional<...>`
- `.apply(ByteBufCodecs.list(N))` → `List<...>` with limit N
- Special mappings: `UUIDUtil` → `UUID`, `ComponentSerialization` → `Component`, `ParticleTypes` → `Particle`

Protocol index assignment (`parse_protocol_file`):
- Parses `GameProtocols.java`, `LoginProtocols.java`, etc.
- Finds `clientboundProtocol`/`serverboundProtocol` method calls
- Extracts `.addPacket()` / `.withBundlePacket()` calls in order — packet index = call order (0-indexed)
- Uses balanced parenthesis matching to handle nested expressions

Custom types:
- Records with `StreamCodec.composite()` definitions
- Interfaces/abstract classes with `.dispatch()` codecs (polymorphic types like `RecipeDisplay`, `SlotDisplay`)
- Only types actually referenced by packet fields are included in output

### Testing the analyzer

Run from `scripts/packets/` against the MCSources checkout:

```bash
cd scripts/packets

# Print JSON to stdout
python packet_data.py

# Generate a single version file
python single_packet_data.py 26.1

# Quick stats check
python -c "
import packet_data
a = packet_data.analyze(packet_data.expanduser(packet_data.join('~','IdeaProjects','MCSources','src','main','java','net','minecraft')))
d = a.generate_json()
total = sum(len(p) for proto in d['packets'].values() for p in proto.values())
with_fields = sum(1 for proto in d['packets'].values() for ps in proto.values() for p in ps if p['fields'])
print(f'{total} packets, {with_fields} with fields, {len(d.get(\"types\", {}))} base types')
for proto, dirs in d['packets'].items():
    for direction, packets in dirs.items():
        n = len(packets); nf = sum(1 for p in packets if p['fields'])
        if n > 0: print(f'  {proto}/{direction}: {n} packets, {nf} with fields')
"

# Check all unique field type names
python -c "
import packet_data
a = packet_data.analyze(packet_data.expanduser(packet_data.join('~','IdeaProjects','MCSources','src','main','java','net','minecraft')))
d = a.generate_json()
types = set()
for proto in d['packets'].values():
    for direction in proto.values():
        for p in direction:
            for f in p['fields']:
                types.add(f['type'])
for t in sorted(types): print(t)
"

# List packets with no fields (should mostly be StreamCodec.unit empty packets)
python -c "
import packet_data
a = packet_data.analyze(packet_data.expanduser(packet_data.join('~','IdeaProjects','MCSources','src','main','java','net','minecraft')))
d = a.generate_json()
for proto, dirs in d['packets'].items():
    for direction, packets in dirs.items():
        for p in packets:
            if not p['fields']:
                print(f'{proto}/{direction}: {p[\"name\"]}')
"
```

### Known limitations

- Packets with complex dispatch in `write()` (e.g. `ClientboundBossEventPacket` with inner `OperationType` enum) produce empty field lists
- `writeCollection` / `writeOptional` / `writeMap` in write methods are detected but inner types are not resolved (shown as generic `Collection`, `Optional`, `Map`)
- Some packets registered in multiple protocols (e.g. `ClientboundCookieRequestPacket` in LOGIN, CONFIGURATION, and PLAY) appear once per protocol section
- The `write()` method parser requires the `final` keyword handling in the method signature (`void write(final FriendlyByteBuf output)`)
