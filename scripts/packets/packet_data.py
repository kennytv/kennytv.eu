from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set
from os.path import expanduser, join
import os
import re


@dataclass
class PacketField:
    name: str
    field_type: str
    limit: Optional[int] = None


@dataclass
class PacketDefinition:
    name: str
    direction: str
    fields: List[PacketField]
    protocols: List[str] = field(default_factory=list)
    indices: Dict[str, int] = field(default_factory=dict)


@dataclass
class CustomType:
    name: str
    fields: List[PacketField]


# Mapping from ByteBufCodecs constant names to readable type names
BYTEBUF_CODEC_TYPES = {
    'BOOL': 'Boolean',
    'BYTE': 'Byte',
    'SHORT': 'Short',
    'UNSIGNED_SHORT': 'Unsigned Short',
    'INT': 'Int',
    'VAR_INT': 'VarInt',
    'LONG': 'Long',
    'VAR_LONG': 'VarLong',
    'FLOAT': 'Float',
    'DOUBLE': 'Double',
    'ROTATION_BYTE': 'Rotation Byte',
    'OPTIONAL_VAR_INT': 'Optional VarInt',
    'CONTAINER_ID': 'Container ID',
    'RGB_COLOR': 'RGB Color',
    'STRING_UTF8': 'String',
    'PLAYER_NAME': 'Player Name',
    'BYTE_ARRAY': 'Byte Array',
    'LONG_ARRAY': 'Long Array',
    'TAG': 'NBT Tag',
    'TRUSTED_TAG': 'NBT Tag',
    'COMPOUND_TAG': 'Compound Tag',
    'TRUSTED_COMPOUND_TAG': 'Compound Tag',
    'OPTIONAL_COMPOUND_TAG': 'Optional Compound Tag',
    'VECTOR3F': 'Vector3f',
    'QUATERNIONF': 'Quaternionf',
    'GAME_PROFILE_PROPERTIES': 'Game Profile Properties',
    'GAME_PROFILE': 'Game Profile',
}


_SPECIAL_TYPE_NAMES = {
    'UUIDUtil': 'UUID',
    'ComponentSerialization': 'Component',
    'ParticleTypes': 'Particle',
    'NumberFormatTypes': 'NumberFormat',
    'Identifier': 'ResourceLocation',
}


def _clean_type_name(raw: str) -> str:
    """Clean up a raw codec reference into a readable type name."""
    raw = raw.strip()

    # ByteBufCodecs.CONSTANT
    m = re.match(r'ByteBufCodecs\.([A-Z0-9_]+)$', raw)
    if m:
        return BYTEBUF_CODEC_TYPES.get(m.group(1), m.group(1))

    # ByteBufCodecs.stringUtf8(N)
    m = re.match(r'ByteBufCodecs\.stringUtf8\(\d+\)$', raw)
    if m:
        return 'String'

    # ByteBufCodecs.byteArray(N) or ByteBufCodecs.byteArray()
    m = re.match(r'ByteBufCodecs\.byteArray\(?\d*\)?$', raw)
    if m:
        return 'Byte Array'

    # ByteBufCodecs.registry(Registries.X) -> X (cleaned)
    m = re.match(r'ByteBufCodecs\.(?:holder)?[Rr]egistry\(Registries\.(\w+)\)', raw)
    if m:
        name = m.group(1).replace('_', ' ').title().replace(' ', '')
        return name

    # ByteBufCodecs.holderSet(Registries.X) -> HolderSet<X>
    m = re.match(r'ByteBufCodecs\.holderSet\(Registries\.(\w+)\)', raw)
    if m:
        name = m.group(1).replace('_', ' ').title().replace(' ', '')
        return f'HolderSet<{name}>'

    # ByteBufCodecs.holder(Registries.X, ...) -> Holder<X>
    m = re.match(r'ByteBufCodecs\.holder\(Registries\.(\w+)', raw)
    if m:
        name = m.group(1).replace('_', ' ').title().replace(' ', '')
        return f'Holder<{name}>'

    # ByteBufCodecs.idMapper(...) -> ID Mapped
    if raw.startswith('ByteBufCodecs.idMapper'):
        return 'ID Mapped'

    # ByteBufCodecs.optional(inner)
    m = re.match(r'ByteBufCodecs\.optional\((.+)\)$', raw)
    if m:
        inner = _clean_type_name(m.group(1))
        return f'Optional<{inner}>'

    # ByteBufCodecs.collection(..., codec) or ByteBufCodecs.collection(..., codec, max)
    m = re.match(r'ByteBufCodecs\.collection\(.+?,\s*(.+?)(?:,\s*\d+)?\)$', raw)
    if m:
        inner = _clean_type_name(m.group(1))
        return f'Collection<{inner}>'

    # ByteBufCodecs.map(...)
    if raw.startswith('ByteBufCodecs.map('):
        return 'Map'

    # ByteBufCodecs.either(left, right)
    m = re.match(r'ByteBufCodecs\.either\((.+?),\s*(.+)\)$', raw)
    if m:
        left = _clean_type_name(m.group(1))
        right = _clean_type_name(m.group(2))
        return f'Either<{left}, {right}>'

    # ByteBufCodecs.fromCodec(...) / fromCodecTrusted(...)
    if 'fromCodec' in raw:
        return 'NBT'

    # ByteBufCodecs.lenientJson(...)
    if 'lenientJson' in raw:
        return 'JSON'

    # Bare UPPER_CASE codec constant names — strip _STREAM_CODEC/_CODEC suffix first
    if re.match(r'^[A-Z0-9_]+$', raw):
        stripped = re.sub(r'_?(?:STREAM_)?CODEC$', '', raw)
        if stripped:
            return BYTEBUF_CODEC_TYPES.get(stripped, stripped.replace('_', ' ').title())
        return BYTEBUF_CODEC_TYPES.get(raw, raw.replace('_', ' ').title())

    # SomeType.STREAM_CODEC or SomeType.SOME_STREAM_CODEC or SomeType.LP_STREAM_CODEC
    m = re.match(r'([\w]+(?:\.[\w]+)*)\.(?:[A-Z0-9_]*_?)?(?:STREAM_CODEC|CODEC)$', raw)
    if m:
        name = m.group(1).split('.')[-1]
        return _SPECIAL_TYPE_NAMES.get(name, name)

    # SomeType.someCodecMethod(...) -> SomeType
    m = re.match(r'([\w]+(?:\.[\w]+)*)\.(?:[a-z]\w*)\(.*\)$', raw, re.DOTALL)
    if m:
        name = m.group(1).split('.')[-1]
        return _SPECIAL_TYPE_NAMES.get(name, name)

    # Direct special name mapping
    if raw in _SPECIAL_TYPE_NAMES:
        return _SPECIAL_TYPE_NAMES[raw]

    # Fallback: strip common suffixes and return
    cleaned = re.sub(r'\.STREAM_CODEC$', '', raw)
    cleaned = re.sub(r'^ByteBufCodecs\.', '', cleaned)
    return _SPECIAL_TYPE_NAMES.get(cleaned, cleaned) if cleaned else raw


def _split_top_level(text: str, delimiter: str = ',') -> List[str]:
    """Split text by delimiter, respecting nested parentheses, angle brackets, and quotes."""
    parts = []
    depth = 0
    current = []
    in_string = False

    for ch in text:
        if ch == '"' and (not current or current[-1] != '\\'):
            in_string = not in_string
            current.append(ch)
        elif in_string:
            current.append(ch)
        elif ch in '(<':
            depth += 1
            current.append(ch)
        elif ch in ')>':
            depth -= 1
            current.append(ch)
        elif ch == delimiter and depth == 0:
            parts.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)

    if current:
        parts.append(''.join(current).strip())

    return parts


def _parse_wrapper(codec_text: str) -> tuple[str, Optional[int]]:
    """Parse a codec expression that may have chained .apply() wrappers.
    Processes from the OUTERMOST .apply() inward.
    Returns (type_name, limit_or_none)."""

    # Use greedy (.+) to match outermost (last) .apply() call first

    # Check for .apply(ByteBufCodecs::optional) — outermost optional
    m = re.match(r'(.+)\.apply\(ByteBufCodecs::optional\)$', codec_text, re.DOTALL)
    if m:
        inner_type, _ = _parse_wrapper(m.group(1))
        return f'Optional<{inner_type}>', None

    # Check for .apply(ByteBufCodecs.list(N))
    m = re.match(r'(.+)\.apply\(ByteBufCodecs\.list\((\d+)\)\)$', codec_text, re.DOTALL)
    if m:
        inner_type, _ = _parse_wrapper(m.group(1))
        return f'List<{inner_type}>', int(m.group(2))

    # Check for .apply(ByteBufCodecs.list())
    m = re.match(r'(.+)\.apply\(ByteBufCodecs\.list\(\)\)$', codec_text, re.DOTALL)
    if m:
        inner_type, _ = _parse_wrapper(m.group(1))
        return f'List<{inner_type}>', None

    # Check for .apply(ByteBufCodecs.collection(...))
    m = re.match(r'(.+)\.apply\(ByteBufCodecs\.collection\(.*?\)\)$', codec_text, re.DOTALL)
    if m:
        inner_type, _ = _parse_wrapper(m.group(1))
        return f'Collection<{inner_type}>', None

    # Check for WeightedList.streamCodec(inner)
    m = re.match(r'WeightedList\.streamCodec\((.+)\)$', codec_text, re.DOTALL)
    if m:
        inner_type = _clean_type_name(m.group(1))
        return f'WeightedList<{inner_type}>', None

    # No wrapper — just a plain codec
    return _clean_type_name(codec_text), None


def _resolve_write_arg_type(arg: str, write_type_map: dict) -> Optional[str]:
    """Resolve the type from a writeCollection/writeOptional second argument.

    Handles three patterns:
    - Method reference: FriendlyByteBuf::writeUtf -> String
    - Codec reference: UUIDUtil.STREAM_CODEC -> UUID, DATA_LAYER_STREAM_CODEC -> DataLayer
    - Lambda: (buf, val) -> ... -> None (caller falls back)
    """
    arg = arg.strip()

    # Method reference: FriendlyByteBuf::writeX or SomeType::write
    m = re.match(r'(?:\w+)::(\w+)', arg)
    if m:
        method_name = m.group(1)
        if method_name in write_type_map:
            return write_type_map[method_name]
        # SomeType::write — not easily resolvable from the method ref alone
        return None

    # Codec reference: SomeType.STREAM_CODEC or bare STREAM_CODEC constant
    if 'CODEC' in arg or re.match(r'^[A-Z_0-9]+$', arg):
        return _clean_type_name(arg)

    # Lambda: (buf, val) -> ... — not resolvable without deeper analysis
    if '->' in arg:
        return None

    return None


def _extract_write_call_args(line: str, method_name: str) -> Optional[List[str]]:
    """Extract the top-level arguments of a .writeX(...) call from a line.

    Uses balanced parenthesis matching to handle nested parens.
    Returns a list of top-level arguments, or None if not found.
    """
    idx = line.find(f'.{method_name}(')
    if idx < 0:
        return None

    start = idx + len(f'.{method_name}(')
    # Use balanced paren matching
    depth = 1
    pos = start
    while pos < len(line) and depth > 0:
        if line[pos] == '(':
            depth += 1
        elif line[pos] == ')':
            depth -= 1
        pos += 1

    if depth != 0:
        return None

    inner = line[start:pos - 1]
    return _split_top_level(inner)


def _extract_field_generic_types(class_content: str) -> Dict[str, str]:
    """Extract generic type parameters from field declarations.

    Parses declarations like:
        private final List<Entry> entries;
        private final Map<ResourceKey<? extends Registry<?>>, Payload> tags;
        private final Optional<UUID> id;
    Returns a dict mapping field name to the simplified inner generic type(s),
    e.g. {'entries': 'Entry', 'tags': 'ResourceKey, Payload', 'id': 'UUID'}.
    """
    field_types: Dict[str, str] = {}
    # Find patterns like: Collection/List/Map/Optional/Set<...> fieldName
    for m in re.finditer(
        r'(?:List|Collection|Set|Optional|Map|EnumSet)\s*<',
        class_content
    ):
        # Use balanced angle bracket matching to extract the full generic args
        start = m.end()
        depth = 1
        pos = start
        while pos < len(class_content) and depth > 0:
            if class_content[pos] == '<':
                depth += 1
            elif class_content[pos] == '>':
                depth -= 1
            pos += 1

        if depth != 0:
            continue

        generic_args = class_content[start:pos - 1].strip()
        # After the closing >, look for the field name
        after = class_content[pos:pos + 50].strip()
        name_match = re.match(r'(\w+)\s*[;,)]', after)
        if not name_match:
            continue

        field_name = name_match.group(1)
        # Clean up: split by top-level commas, take the simple class name
        top_parts = _split_top_level(generic_args)
        cleaned = []
        for part in top_parts:
            # Strip nested generics for the simplified name: ResourceKey<? extends Foo> -> ResourceKey
            base = re.sub(r'<.*$', '', part.strip())
            # Simplify dotted names, keeping inner class qualifiers
            # e.g. ClientboundCommandsPacket.Entry -> CommandsPacket.Entry
            #      TagNetworkSerialization.NetworkPayload -> NetworkPayload
            #      net.minecraft.Foo -> Foo
            segments = base.split('.')
            if len(segments) >= 2 and segments[-2][0:1].isupper():
                # Inner class: keep OuterClass.InnerClass but shorten "Clientbound/Serverbound" prefix
                outer = re.sub(r'^(?:Clientbound|Serverbound)', '', segments[-2])
                base = f'{outer}.{segments[-1]}'
            else:
                base = segments[-1]
            # Strip wildcards
            base = base.replace('?', '').strip()
            if base:
                cleaned.append(base)
        if cleaned:
            field_types[field_name] = ', '.join(cleaned)
    return field_types


def _resolve_wrapper_type(wrapper: str, line: str, write_type_map: dict,
                          field_generics: Optional[Dict[str, str]] = None) -> str:
    """Resolve writeCollection/writeOptional to e.g. Collection<String>.

    Args:
        wrapper: 'Collection' or 'Optional'
        line: the full source line
        write_type_map: mapping from writeX method names to types
        field_generics: optional mapping of field names to their declared generic types
    """
    method = 'writeCollection' if wrapper == 'Collection' else 'writeOptional'
    args = _extract_write_call_args(line, method)

    if args and len(args) >= 2:
        inner = _resolve_write_arg_type(args[1], write_type_map)
        if inner:
            return f'{wrapper}<{inner}>'

        # Fallback: if the second arg is a lambda, try to resolve from field declarations
        if field_generics and '->' in args[1]:
            field_match = re.search(r'this\.(\w+)', args[0])
            if field_match:
                field_name = field_match.group(1)
                if field_name in field_generics:
                    return f'{wrapper}<{field_generics[field_name]}>'

    # Fallback for multi-line lambdas where arg extraction fails (unbalanced parens)
    if args is None and field_generics and '->' in line:
        field_match = re.search(r'this\.(\w+)', line)
        if field_match:
            field_name = field_match.group(1)
            if field_name in field_generics:
                return f'{wrapper}<{field_generics[field_name]}>'

    return wrapper


def _resolve_map_type(line: str, write_type_map: dict,
                      field_generics: Optional[Dict[str, str]] = None) -> str:
    """Resolve writeMap to e.g. Map<ResourceKey, TagPayload>.

    writeMap(this.field, keyWriter, valueWriter)
    """
    args = _extract_write_call_args(line, 'writeMap')

    if args and len(args) >= 3:
        key_type = _resolve_write_arg_type(args[1], write_type_map)
        val_type = _resolve_write_arg_type(args[2], write_type_map)
        if key_type and val_type:
            return f'Map<{key_type}, {val_type}>'

        # Fallback: try field declarations for unresolved parts
        if field_generics and (not key_type or not val_type):
            field_match = re.search(r'this\.(\w+)', args[0])
            if field_match and field_match.group(1) in field_generics:
                # field_generics for a Map field is "KeyType, ValueType"
                return f'Map<{field_generics[field_match.group(1)]}>'

        if key_type and not val_type:
            return f'Map<{key_type}, ?>'
        elif val_type and not key_type:
            return f'Map<?, {val_type}>'

    return 'Map'


class MinecraftPacketAnalyzer:
    def __init__(self, source_dir: str):
        self.source_dir = source_dir
        self.packet_definitions: Dict[str, PacketDefinition] = {
            "ClientboundBundlePacket": PacketDefinition(
                name="ClientboundBundlePacket",
                direction="CLIENTBOUND",
                protocols=["PLAY"],
                fields=[]
            )
        }
        self.custom_types: Dict[str, CustomType] = {}
        self.protocol_files = {
            "HANDSHAKE": "HandshakeProtocols",
            "STATUS": "StatusProtocols",
            "LOGIN": "LoginProtocols",
            "CONFIGURATION": "ConfigurationProtocols",
            "PLAY": "GameProtocols"
        }

    def _parse_composite_args(self, args_text: str) -> List[PacketField]:
        """Parse the arguments of StreamCodec.composite(...) into PacketField list.
        Arguments come in pairs: (codec, getter) with the last arg being the constructor."""
        parts = _split_top_level(args_text)
        fields = []

        # Process in pairs; the last element is the constructor reference
        i = 0
        while i + 1 < len(parts):
            codec_text = parts[i].strip()
            getter_text = parts[i + 1].strip()

            # Extract field name from getter (ClassName::fieldName)
            name_match = re.search(r'::(\w+)', getter_text)
            field_name = name_match.group(1) if name_match else 'unknown'

            field_type, limit = _parse_wrapper(codec_text)
            fields.append(PacketField(name=field_name, field_type=field_type, limit=limit))
            i += 2

        return fields

    def _parse_write_method(self, write_body: str, class_content: str = '') -> List[PacketField]:
        """Parse a write() method body to extract fields from write calls."""
        fields = []
        field_generics = _extract_field_generic_types(class_content) if class_content else {}

        # Map from writeX method names to clean type names
        write_type_map = {
            'writeVarInt': 'VarInt', 'writeVarLong': 'VarLong',
            'writeInt': 'Int', 'writeLong': 'Long',
            'writeShort': 'Short', 'writeByte': 'Byte',
            'writeFloat': 'Float', 'writeDouble': 'Double',
            'writeBoolean': 'Boolean', 'writeUUID': 'UUID',
            'writeUtf': 'String', 'writeByteArray': 'Byte Array',
            'writeBlockPos': 'BlockPos', 'writeComponent': 'Component',
            'writeResourceLocation': 'ResourceLocation',
            'writeGlobalPos': 'GlobalPos', 'writeNbt': 'NBT',
            'writeResourceKey': 'ResourceKey',
            'writeSectionPos': 'SectionPos', 'writeChunkPos': 'ChunkPos',
            'writeInstant': 'Instant', 'writeBlockHitResult': 'BlockHitResult',
            'writeBitSet': 'BitSet',
            'writeContainerId': 'Container ID',
            'writeLongArray': 'Long Array',
            'writeEnum': 'Enum',
            'writeIntIdList': 'VarInt List',
            'writeJsonWithCodec': 'JSON',
            'writeWithCodec': 'NBT',
            'writeId': 'Registry ID',
            'writeBytes': 'Bytes',
            'writeIdentifier': 'ResourceLocation',
        }

        # Patterns applied per-line, checked in order
        line_patterns = [
            # output.writeVarInt(this.x) etc. — direct primitive writes
            (re.compile(r'\.(' + '|'.join(write_type_map.keys()) + r')\s*\('),
             lambda m, _line: write_type_map[m.group(1)]),

            # output.writeCollection(this.x, SomeCodec) or writeCollection(this.x, (buf, val) -> ...)
            (re.compile(r'\.writeCollection\('),
             lambda m, _line: _resolve_wrapper_type('Collection', _line, write_type_map, field_generics)),

            # output.writeOptional(this.x, SomeCodec)
            (re.compile(r'\.writeOptional\('),
             lambda m, _line: _resolve_wrapper_type('Optional', _line, write_type_map, field_generics)),

            # output.writeMap(...)
            (re.compile(r'\.writeMap\('),
             lambda m, _line: _resolve_map_type(_line, write_type_map, field_generics)),

            # output.writeEnumSet(...)
            (re.compile(r'\.writeEnumSet\('),
             lambda m, _line: 'EnumSet'),

            # SomeCodec.encode(output, this.x)
            (re.compile(r'([\w.]+(?:STREAM_CODEC|CODEC))\.encode\('),
             lambda m, _line: _clean_type_name(m.group(1))),

            # ByteBufCodecs.registry(...).encode(output, this.x)
            (re.compile(r'ByteBufCodecs\.(?:holder)?[Rr]egistry\(Registries\.(\w+)\)\.encode\('),
             lambda m, _line: m.group(1).replace('_', ' ').title().replace(' ', '')),

            # Static write helper: SomeType.write(output, this.field) or SomeType.SomeMethod.write(output, this.field)
            (re.compile(r'([\w.]+)\.write\(\s*\w+\s*,\s*this\.'),
             lambda m, _line: _clean_type_name(m.group(1))),
        ]

        for line in write_body.split('\n'):
            line = line.strip()
            if not line or line.startswith('//') or line.startswith('/*'):
                continue

            # Skip control flow, method calls that aren't writes, etc.
            # Only process lines that look like they write data
            if not ('write' in line.lower() or '.encode(' in line or 'CODEC' in line):
                continue

            for pattern, type_extractor in line_patterns:
                match = pattern.search(line)
                if match:
                    # Try to extract field name from this.fieldName or this.fieldName()
                    field_match = re.search(r'this\.(\w+?)(?:\(\))?(?:\)|,|\s)', line)
                    field_name = field_match.group(1) if field_match else 'unknown'
                    field_type = type_extractor(match, line)
                    fields.append(PacketField(name=field_name, field_type=field_type))
                    break

        return fields

    def _find_write_method_body(self, content: str) -> Optional[str]:
        """Find the write() method and extract its body using brace matching."""
        write_sig = re.search(
            r'(?:public\s+|private\s+)?void\s+write\s*\(\s*(?:final\s+)?(?:Registry)?FriendlyByteBuf\s+\w+\s*\)\s*\{',
            content
        )
        if not write_sig:
            return None

        # Find the opening brace and match it
        brace_start = write_sig.end() - 1  # position of '{'
        end = self._find_class_end(content, brace_start)
        return content[brace_start + 1:end - 1]  # body without outer braces

    def _find_stream_codec(self, content: str, class_name: str) -> Optional[List[PacketField]]:
        """Find and parse the STREAM_CODEC definition for a class within content."""

        # Pattern 1: StreamCodec.composite(...)
        composite_pattern = re.compile(
            r'STREAM_CODEC\s*=\s*StreamCodec\.composite\((.*?)\);',
            re.DOTALL
        )

        # Pattern 2: Packet.codec(Writer::write, Reader::new/read)
        packet_codec_pattern = re.compile(
            r'STREAM_CODEC\s*=\s*Packet\.codec\(\s*\w+::write\s*,\s*\w+::\w+\s*\)\s*;',
            re.DOTALL
        )

        # Pattern 3: StreamCodec.unit(...)
        unit_pattern = re.compile(
            r'STREAM_CODEC\s*=\s*StreamCodec\.unit\(',
            re.DOTALL
        )

        # Pattern 4: SomeCodec.map(ClassName::new, ClassName::getter) — single-field wrapper
        map_pattern = re.compile(
            r'STREAM_CODEC\s*=\s*(.+?)\.map\(\s*\w+::new\s*,\s*\w+::(\w+)\s*\)\s*;',
            re.DOTALL
        )

        # Pattern 5: StreamCodec.ofMember(Writer::write, Reader::new)
        of_member_pattern = re.compile(
            r'STREAM_CODEC\s*=\s*StreamCodec\.ofMember\(\s*\w+::write\s*,\s*\w+::\w+\s*\)\s*;',
            re.DOTALL
        )

        # Try composite first
        match = composite_pattern.search(content)
        if match:
            return self._parse_composite_args(match.group(1))

        # Try unit (empty packet)
        if unit_pattern.search(content):
            return []

        # Try .map() pattern (single-field packet)
        match = map_pattern.search(content)
        if match:
            codec_text = match.group(1).strip()
            field_name = match.group(2)
            # Only use this if the codec text is simple (no multi-line lambda expressions)
            if '\n' not in codec_text or len(codec_text) < 120:
                field_type, limit = _parse_wrapper(codec_text)
                return [PacketField(name=field_name, field_type=field_type, limit=limit)]
            # Complex codec text — fall through to write method parsing

        # Try Packet.codec or StreamCodec.ofMember (manual write/read)
        if packet_codec_pattern.search(content) or of_member_pattern.search(content):
            write_body = self._find_write_method_body(content)
            if write_body:
                return self._parse_write_method(write_body, content)
            return []

        return None

    def _find_class_end(self, content: str, start_pos: int) -> int:
        """Find the end of a class/record body using brace matching."""
        level = 0
        in_string = False
        for i in range(start_pos, len(content)):
            ch = content[i]
            if ch == '"' and (i == 0 or content[i - 1] != '\\'):
                in_string = not in_string
            elif not in_string:
                if ch == '{':
                    level += 1
                elif ch == '}':
                    level -= 1
                    if level == 0:
                        return i + 1
        return len(content)

    def parse_packet_file(self, file_path: str) -> Optional[List[PacketDefinition]]:
        """Parse a Java file for packet definitions."""
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        if 'implements Packet<' not in content:
            return None

        # Match packet class declarations
        packet_pattern = re.compile(
            r'public\s+(?:abstract\s+)?(?:record|class)\s+(\w+)(?:\s*\([^)]*\))?\s+'
            r'(?:extends\s+\w+\s+)?implements\s+Packet<(\w+)>',
            re.DOTALL
        )

        base_match = packet_pattern.search(content)
        if not base_match:
            return None

        base_name = base_match.group(1)
        direction = "CLIENTBOUND" if "Clientbound" in base_name or "clientbound" in file_path.replace('\\', '/').lower() else "SERVERBOUND"

        packet_defs = []

        # Look for inner static classes that also have STREAM_CODEC
        inner_class_pattern = re.compile(
            r'public\s+static\s+(?:record|class)\s+(\w+)(?:\s*\([^)]*\))?\s+'
            r'(?:extends\s+\w+\s+)?'
        )

        for inner_match in inner_class_pattern.finditer(content):
            inner_name = inner_match.group(1)
            inner_start = inner_match.start()
            inner_end = self._find_class_end(content, content.index('{', inner_start))
            inner_content = content[inner_start:inner_end]

            # Check if inner class has its own STREAM_CODEC
            if 'STREAM_CODEC' not in inner_content:
                continue

            fields = self._find_stream_codec(inner_content, inner_name)
            if fields is not None:
                full_name = f"{base_name}.{inner_name}"
                packet_defs.append(PacketDefinition(
                    name=full_name,
                    direction=direction,
                    fields=fields
                ))

        # Parse the base class itself (if it has a STREAM_CODEC and is not abstract)
        if 'abstract' not in content[:base_match.start() + len(base_match.group(0))].split('\n')[-1]:
            fields = self._find_stream_codec(content, base_name)
            if fields is not None:
                packet_defs.append(PacketDefinition(
                    name=base_name,
                    direction=direction,
                    fields=fields
                ))

        return packet_defs if packet_defs else None

    def parse_custom_type_file(self, file_path: str):
        """Parse a Java file for custom codec types (records/classes/interfaces with STREAM_CODEC that aren't packets)."""
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Skip packet files (but still check inner types)
        if 'implements Packet<' in content:
            self._parse_inner_types(content)
            return

        # Skip CustomPacketPayload types
        if 'implements CustomPacketPayload' in content:
            return

        # Look for record definitions with STREAM_CODEC
        record_pattern = re.compile(
            r'public\s+(?:static\s+)?record\s+(\w+)\s*\((.*?)\)\s*(?:implements\s+[^{]+)?\s*\{',
            re.DOTALL
        )

        for type_match in record_pattern.finditer(content):
            type_name = type_match.group(1)
            type_start = type_match.start()
            brace_pos = content.index('{', type_start)
            type_end = self._find_class_end(content, brace_pos)
            type_content = content[type_start:type_end]

            if 'STREAM_CODEC' not in type_content:
                continue

            fields = self._find_stream_codec(type_content, type_name)
            if fields is not None and fields:
                self.custom_types[type_name] = CustomType(name=type_name, fields=fields)

        # Look for interfaces/classes with STREAM_CODEC using dispatch (polymorphic types)
        # These are types like RecipeDisplay, SlotDisplay, ParticleOptions that use .dispatch()
        dispatch_pattern = re.compile(
            r'(?:public\s+)?(?:interface|abstract\s+class)\s+(\w+)(?:\s+extends\s+\w+)?\s*\{',
        )
        for match in dispatch_pattern.finditer(content):
            type_name = match.group(1)
            brace_pos = match.end() - 1
            type_end = self._find_class_end(content, brace_pos)
            type_content = content[brace_pos:type_end]

            # Check for dispatch codec pattern
            if '.dispatch(' in type_content and 'STREAM_CODEC' in type_content:
                if type_name not in self.custom_types:
                    self.custom_types[type_name] = CustomType(name=type_name, fields=[])

    def _parse_inner_types(self, content: str):
        """Parse inner record types within packet files that have their own STREAM_CODEC."""
        inner_record_pattern = re.compile(
            r'public\s+static\s+record\s+(\w+)\s*\((.*?)\)\s*(?:implements\s+[^{]+)?\s*\{',
            re.DOTALL
        )

        for match in inner_record_pattern.finditer(content):
            type_name = match.group(1)
            brace_pos = content.index('{', match.start())
            type_end = self._find_class_end(content, brace_pos)
            type_content = content[match.start():type_end]

            if 'STREAM_CODEC' not in type_content or 'implements Packet<' in type_content:
                continue

            fields = self._find_stream_codec(type_content, type_name)
            if fields is not None and fields:
                self.custom_types[type_name] = CustomType(name=type_name, fields=fields)

    def parse_protocol_file(self, file_path: str, protocol: str):
        """Parse a protocol registration file to assign packet indices."""
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Find protocol builder method calls and extract the full section using brace/paren matching
        method_pattern = re.compile(
            r'((?:context)?(?:Serverbound|Clientbound|serverbound|clientbound)Protocol)\s*\(\s*ConnectionProtocol\.\w+',
        )

        for method_match in method_pattern.finditer(content):
            method_name = method_match.group(1).lower()
            direction = "CLIENTBOUND" if "clientbound" in method_name else "SERVERBOUND"

            # Find the opening paren of the method call and extract to the matching close
            paren_start = content.index('(', method_match.start())
            section_end = self._find_matching_paren(content, paren_start)
            section = content[paren_start:section_end]

            # Find all addPacket/withBundlePacket calls in order within this section
            reg_pattern = re.compile(
                r'\.(?:addPacket|withBundlePacket)\(\s*\w+Types?\.(\w+)\s*,\s*'
                r'(\w+)(?:\.(\w+))?'
                r'(?:\.[A-Z_]*STREAM_CODEC|::new)',
                re.MULTILINE
            )

            current_index = 0
            for reg_match in reg_pattern.finditer(section):
                packet_type_name = reg_match.group(1)
                packet_class = reg_match.group(2)
                subclass = reg_match.group(3)

                full_class = f"{packet_class}.{subclass}" if subclass else packet_class

                # Try full name first, then base name
                packet = self.packet_definitions.get(full_class) or self.packet_definitions.get(packet_class)

                if packet:
                    if protocol not in packet.protocols:
                        packet.protocols.append(protocol)
                    packet.indices[protocol] = current_index
                    current_index += 1

    @staticmethod
    def _find_matching_paren(content: str, start: int) -> int:
        """Find the position after the matching closing parenthesis."""
        depth = 0
        in_string = False
        for i in range(start, len(content)):
            ch = content[i]
            if ch == '"' and (i == 0 or content[i - 1] != '\\'):
                in_string = not in_string
            elif not in_string:
                if ch == '(':
                    depth += 1
                elif ch == ')':
                    depth -= 1
                    if depth == 0:
                        return i + 1
        return len(content)

    def find_and_parse_files(self):
        """Walk the source directory and parse all relevant files."""
        processed_for_types: Set[str] = set()

        for root, _, files in os.walk(self.source_dir):
            for file in files:
                if not file.endswith('.java'):
                    continue
                file_path = os.path.join(root, file)

                # Parse packet definitions
                packet_defs = self.parse_packet_file(file_path)
                if packet_defs:
                    for packet in packet_defs:
                        self.packet_definitions[packet.name] = packet

                # Parse custom types
                if file_path not in processed_for_types:
                    processed_for_types.add(file_path)
                    self.parse_custom_type_file(file_path)

    def assign_packet_indices(self):
        """Find and parse protocol files to assign indices to packets."""
        for protocol, filename in self.protocol_files.items():
            protocol_path = None
            for root, _, files in os.walk(self.source_dir):
                if f"{filename}.java" in files:
                    protocol_path = os.path.join(root, f"{filename}.java")
                    break

            if protocol_path:
                self.parse_protocol_file(protocol_path, protocol)

    def generate_json(self) -> Dict[str, Any]:
        """Generate a JSON-serializable dict of all packet data for one version."""
        protocols_order = ["HANDSHAKE", "STATUS", "LOGIN", "CONFIGURATION", "PLAY"]
        directions = ["CLIENTBOUND", "SERVERBOUND"]

        packets: Dict[str, Dict[str, list]] = {}
        for protocol in protocols_order:
            packets[protocol] = {"CLIENTBOUND": [], "SERVERBOUND": []}

        # Group packets by protocol and direction
        for packet in self.packet_definitions.values():
            for protocol in packet.protocols:
                if protocol in packets:
                    entry = {
                        "name": packet.name,
                        "index": packet.indices.get(protocol, -1),
                        "fields": [
                            self._field_to_json(f) for f in packet.fields
                        ]
                    }
                    packets[protocol][packet.direction].append(entry)

        # Sort packets within each section by index
        for protocol in packets:
            for direction in directions:
                packets[protocol][direction].sort(key=lambda p: p["index"])

        # Build types section (only include types actually referenced)
        referenced = self._find_referenced_types(packets)
        types = {}
        for name in sorted(referenced):
            if name in self.custom_types:
                ct = self.custom_types[name]
                types[name] = {
                    "fields": [self._field_to_json(f) for f in ct.fields]
                }

        result: Dict[str, Any] = {"packets": packets}
        if types:
            result["types"] = types
        return result

    def _field_to_json(self, f: PacketField) -> Dict[str, Any]:
        entry: Dict[str, Any] = {"name": f.name, "type": f.field_type}
        if f.limit is not None:
            entry["limit"] = f.limit
        return entry

    def _find_referenced_types(self, packets: Dict) -> Set[str]:
        """Find all custom type names referenced by packet fields."""
        referenced = set()
        for protocol in packets.values():
            for direction in protocol.values():
                for packet in direction:
                    for field_data in packet["fields"]:
                        self._collect_type_refs(field_data["type"], referenced)
        return referenced

    def _collect_type_refs(self, type_str: str, refs: Set[str]):
        """Recursively collect custom type references from a type string."""
        # Check if the base name (without wrappers) is a known custom type
        # Strip wrappers like Optional<X>, List<X>, etc.
        inner = re.match(r'(?:Optional|List|Collection|WeightedList|HolderSet|Holder|Map|Either)<(.+?)>$', type_str)
        if inner:
            self._collect_type_refs(inner.group(1), refs)
            return

        if type_str in self.custom_types:
            refs.add(type_str)


def analyze(source_dir: str) -> MinecraftPacketAnalyzer:
    """Run the analyzer against a source directory and return it."""
    analyzer = MinecraftPacketAnalyzer(source_dir)
    analyzer.find_and_parse_files()
    analyzer.assign_packet_indices()
    return analyzer


def main():
    source_dir = expanduser(join("~", "IdeaProjects", "MCSources", "src", "main", "java", "net", "minecraft"))
    analyzer = analyze(source_dir)
    json_data = analyzer.generate_json()

    import json
    print(json.dumps(json_data, indent=2))


if __name__ == "__main__":
    main()
