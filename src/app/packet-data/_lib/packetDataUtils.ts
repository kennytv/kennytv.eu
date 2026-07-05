/* ── Types ──────────────────────────────────────────── */

/** A node in the recursive wire-format tree produced by the extractor. */
export interface PacketNode {
  kind: string;
  name?: string;
  java?: string;
  wire?: string;
  context?: string;
  ref?: string;
  registry?: string;
  values?: string[];
  limit?: number;
  note?: string;
  partial?: boolean;
  /** Repetition count for merged fixed-size reads (e.g. 3 consecutive bytes). */
  x?: number;
  /** Optional related page (e.g. /entity-data for serializer formats). */
  link?: string;
  linkText?: string;
  fields?: PacketNode[];
  inner?: PacketNode;
  elem?: PacketNode;
  key?: PacketNode;
  value?: PacketNode;
  left?: PacketNode;
  right?: PacketNode;
  direct?: PacketNode;
  variants?: PacketVariant[];
}

export interface PacketVariant {
  key: string;
  body: PacketNode;
}

export interface PacketInfo {
  index: number;
  id: string;
  class?: string;
  body: PacketNode;
}

export interface VersionPacketData {
  version: string;
  protocolVersion?: number;
  protocols: Record<string, Record<string, PacketInfo[]>>;
  types: Record<string, PacketNode>;
}

export interface PacketDataIndex {
  versions: string[];
}

export const PROTOCOL_ORDER = ['handshake', 'status', 'login', 'configuration', 'play'] as const;
export const FLOW_ORDER = ['clientbound', 'serverbound'] as const;

export const PROTOCOL_LABELS: Record<string, string> = {
  handshake: 'Handshake',
  status: 'Status',
  login: 'Login',
  configuration: 'Configuration',
  play: 'Play',
};

export const FLOW_LABELS: Record<string, string> = {
  clientbound: 'Clientbound',
  serverbound: 'Serverbound',
};

/* ── Wire encodings ────────────────────────────────── */

/** How primitive wire values are encoded — shown as hover tooltips. */
export const WIRE_ENCODINGS: Record<string, string> = {
  VarInt: '1–5 bytes of 7 bits each, least significant first; the high bit of a byte signals that another follows',
  VarLong: '1–10 bytes of 7 bits each, least significant first; the high bit of a byte signals that another follows',
  String: 'VarInt byte length, then UTF-8 bytes',
  Identifier: 'String of the form namespace:path (namespace defaults to minecraft)',
  'JSON (String)': 'String containing JSON',
  UUID: 'two Longs: most significant half, then least significant half',
  BlockPos: 'one Long: x (26 bits) at the top, then z (26 bits), then y (12 bits) at the bottom',
  ChunkPos: 'one Long: chunk x in the low 32 bits, chunk z in the high 32 bits',
  GlobalPos: 'dimension Identifier, then BlockPos',
  'Byte Array': 'VarInt length, then that many bytes',
  'Long Array': 'VarInt length, then that many Longs',
  'Var Int Array': 'VarInt count, then that many VarInts',
  'VarInt List': 'VarInt count, then that many VarInts',
  NBT: 'network NBT: tag type byte, then the unnamed payload',
  EnumSet: 'fixed bit set: one bit per enum constant, packed low-to-high into ⌈n/8⌉ bytes',
  BitSet: 'VarInt length in Longs, then that many Longs',
  'BitSet (fixed)': 'fixed number of bits packed into ⌈n/8⌉ bytes',
  Boolean: 'one byte: 0 or 1',
  Byte: 'one signed byte',
  'Unsigned Byte': 'one unsigned byte',
  Short: 'two bytes, big-endian, signed',
  'Unsigned Short': 'two bytes, big-endian, unsigned',
  Int: 'four bytes, big-endian, signed',
  'Unsigned Int': 'four bytes, big-endian, unsigned',
  Long: 'eight bytes, big-endian, signed',
  'Long (Instant)': 'Long: milliseconds since the Unix epoch',
  Float: 'four bytes, IEEE 754',
  Double: 'eight bytes, IEEE 754',
  Enum: 'VarInt ordinal of the constant, unless noted otherwise',
  'Public Key': 'VarInt length, then DER-encoded key bytes',
  'Registry Key': 'Identifier',
  'Resource Key': 'Identifier',
};

/** How container shapes are encoded — shown as hover tooltips. */
export const CONTAINER_ENCODINGS: Record<string, string> = {
  List: 'VarInt element count, then that many elements',
  Optional: 'Boolean presence flag, then the value only if true',
  Map: 'VarInt entry count, then that many key/value pairs',
  'Length-prefixed': 'VarInt byte length, then the content',
  Prefixed: 'VarInt byte length, then the content',
  Either: 'one Boolean: true = first type follows, false = second',
  Dispatch: 'the body depends on the value of a preceding id/enum field',
};

/* ── Node traversal ────────────────────────────────── */

export function childEntries(node: PacketNode): { label?: string; node: PacketNode }[] {
  const out: { label?: string; node: PacketNode }[] = [];
  if (node.fields) for (const f of node.fields) out.push({ node: f });
  if (node.inner) out.push({ label: 'inner', node: node.inner });
  if (node.elem) out.push({ label: 'element', node: node.elem });
  if (node.key) out.push({ label: 'key', node: node.key });
  if (node.value) out.push({ label: 'value', node: node.value });
  if (node.left) out.push({ label: 'if true', node: node.left });
  if (node.right) out.push({ label: 'if false', node: node.right });
  if (node.direct) out.push({ label: 'inline value (id 0)', node: node.direct });
  return out;
}

/** Human-readable type/wire label for a node row. */
export function nodeTypeLabel(node: PacketNode): string {
  switch (node.kind) {
    case 'value':
      return node.wire ?? '?';
    case 'ref':
      return node.ref ?? '?';
    case 'registry':
      return `VarInt (${shortRegistry(node.registry)} id)`;
    case 'holder':
      return `VarInt (0 = inline value, else ${shortRegistry(node.registry)} id + 1)`;
    case 'holderSet':
      return `VarInt (0 = tag Identifier, else count + 1, then ${shortRegistry(node.registry)} ids)`;
    case 'enum':
      return `${node.wire ?? 'VarInt'} enum${node.java ? ` (${node.java})` : ''}`;
    case 'list':
      return 'List';
    case 'map':
      return 'Map';
    case 'optional':
      return 'Optional';
    case 'either':
      return 'Either (Boolean)';
    case 'prefixed':
      return 'Length-prefixed';
    case 'dispatch':
      return 'Dispatch';
    case 'unit':
      return 'nothing';
    case 'container':
    case 'traced':
      return node.java ?? '';
    case 'group':
      return node.context ?? '';
    case 'opaque':
      return 'unknown';
    default:
      return node.kind;
  }
}

export function shortRegistry(registry?: string): string {
  if (!registry) return '?';
  return registry.replace('minecraft:', '');
}

/* ── Search ────────────────────────────────────────── */

/** "70" or "0x46" → 70; null for non-numeric queries. */
export function parseIdQuery(lower: string): number | null {
  if (/^0x[0-9a-f]+$/.test(lower)) return parseInt(lower.slice(2), 16);
  if (/^\d+$/.test(lower)) return parseInt(lower, 10);
  return null;
}

export function nodeMatches(node: PacketNode, lower: string): boolean {
  if (node.name?.toLowerCase().includes(lower)) return true;
  if (node.wire?.toLowerCase().includes(lower)) return true;
  if (node.java?.toLowerCase().includes(lower)) return true;
  if (node.ref?.toLowerCase().includes(lower)) return true;
  if (node.registry?.toLowerCase().includes(lower)) return true;
  if (node.context?.toLowerCase().includes(lower)) return true;
  for (const { node: child } of childEntries(node)) {
    if (nodeMatches(child, lower)) return true;
  }
  for (const v of node.variants ?? []) {
    if (v.key.toLowerCase().includes(lower) || nodeMatches(v.body, lower)) return true;
  }
  return false;
}

export function packetMatches(packet: PacketInfo, lower: string): boolean {
  if (!lower) return true;
  const idQuery = parseIdQuery(lower);
  if (idQuery != null && packet.index === idQuery) return true;
  if (packet.id.toLowerCase().includes(lower)) return true;
  if (packet.class?.toLowerCase().includes(lower)) return true;
  return nodeMatches(packet.body, lower);
}

/** Shared types matching the query by name or content. */
export function filterTypes(types: Record<string, PacketNode>, query: string): Record<string, PacketNode> {
  const lower = query.toLowerCase().trim();
  if (!lower) return types;
  const out: Record<string, PacketNode> = {};
  for (const [name, node] of Object.entries(types)) {
    if (name.toLowerCase().includes(lower) || nodeMatches(node, lower)) out[name] = node;
  }
  return out;
}

export interface FilteredSection {
  protocol: string;
  flow: string;
  packets: PacketInfo[];
}

export function filterPackets(data: VersionPacketData, query: string): FilteredSection[] {
  const lower = query.toLowerCase().trim();
  const out: FilteredSection[] = [];
  for (const protocol of PROTOCOL_ORDER) {
    const flows = data.protocols[protocol];
    if (!flows) continue;
    for (const flow of FLOW_ORDER) {
      const packets = flows[flow];
      if (!packets || packets.length === 0) continue;
      const filtered = lower ? packets.filter((p) => packetMatches(p, lower)) : packets;
      if (filtered.length > 0) out.push({ protocol, flow, packets: filtered });
    }
  }
  return out;
}

export function getTotalPacketCount(data: VersionPacketData): number {
  let count = 0;
  for (const flows of Object.values(data.protocols)) {
    for (const packets of Object.values(flows)) count += packets.length;
  }
  return count;
}

export function getFilteredCount(sections: FilteredSection[]): number {
  return sections.reduce((sum, s) => sum + s.packets.length, 0);
}

/* ── Diff ──────────────────────────────────────────── */

/** Flatten a node tree into indented text rows for line diffing. */
export function flattenNode(node: PacketNode, depth = 0, label?: string): string[] {
  const indent = '  '.repeat(depth);
  const name = node.name ?? label;
  const type = nodeTypeLabel(node);
  const times = node.x != null ? ` ×${node.x}` : '';
  const java = node.java && node.java !== type ? ` — ${node.java}` : '';
  const limit = node.limit != null ? ` (max ${node.limit})` : '';
  const rows: string[] = [`${indent}${name ? `${name}: ` : ''}${type}${times}${limit}${java}`];
  for (const { label: childLabel, node: child } of childEntries(node)) {
    rows.push(...flattenNode(child, depth + 1, childLabel));
  }
  for (const v of node.variants ?? []) {
    rows.push(`${indent}  » ${v.key}`);
    for (const { label: childLabel, node: child } of childEntries(v.body)) {
      rows.push(...flattenNode(child, depth + 2, childLabel));
    }
  }
  return rows;
}

export type DiffLineStatus = 'same' | 'added' | 'removed';

export interface DiffLine {
  status: DiffLineStatus;
  text: string;
}

/** Simple LCS-based line diff. */
export function diffLines(left: string[], right: string[]): DiffLine[] {
  const n = left.length;
  const m = right.length;
  const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i][j] = left[i] === right[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (left[i] === right[j]) {
      out.push({ status: 'same', text: left[i] });
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ status: 'removed', text: left[i] });
      i++;
    } else {
      out.push({ status: 'added', text: right[j] });
      j++;
    }
  }
  while (i < n) out.push({ status: 'removed', text: left[i++] });
  while (j < m) out.push({ status: 'added', text: right[j++] });
  return out;
}

export type PacketDiffStatus = 'unchanged' | 'added' | 'removed' | 'changed';

export interface PacketDiff {
  status: PacketDiffStatus;
  id: string;
  class?: string;
  protocol: string;
  flow: string;
  leftIndex?: number;
  rightIndex?: number;
  lines: DiffLine[];
}

export function computePacketDiff(left: VersionPacketData, right: VersionPacketData): PacketDiff[] {
  const diffs: PacketDiff[] = [];
  for (const protocol of PROTOCOL_ORDER) {
    for (const flow of FLOW_ORDER) {
      const leftPackets = left.protocols[protocol]?.[flow] ?? [];
      const rightPackets = right.protocols[protocol]?.[flow] ?? [];
      const leftById = new Map(leftPackets.map((p) => [p.id, p]));
      const rightById = new Map(rightPackets.map((p) => [p.id, p]));
      const ids = [...new Set([...leftById.keys(), ...rightById.keys()])].sort();

      for (const id of ids) {
        const l = leftById.get(id);
        const r = rightById.get(id);
        if (!l && r) {
          diffs.push({
            status: 'added', id, class: r.class, protocol, flow, rightIndex: r.index,
            lines: flattenNode(r.body).map((text) => ({ status: 'added', text })),
          });
        } else if (l && !r) {
          diffs.push({
            status: 'removed', id, class: l.class, protocol, flow, leftIndex: l.index,
            lines: flattenNode(l.body).map((text) => ({ status: 'removed', text })),
          });
        } else if (l && r) {
          const leftRows = flattenNode(l.body);
          const rightRows = flattenNode(r.body);
          const bodyChanged = JSON.stringify(leftRows) !== JSON.stringify(rightRows);
          const changed = bodyChanged || l.index !== r.index;
          diffs.push({
            status: changed ? 'changed' : 'unchanged',
            id, class: r.class, protocol, flow,
            leftIndex: l.index, rightIndex: r.index,
            lines: bodyChanged ? diffLines(leftRows, rightRows) : leftRows.map((text) => ({ status: 'same', text })),
          });
        }
      }
    }
  }
  return diffs;
}

export function filterPacketDiffs(diffs: PacketDiff[], query: string): PacketDiff[] {
  if (!query.trim()) return diffs;
  const lower = query.toLowerCase().trim();
  const idQuery = parseIdQuery(lower);
  return diffs.filter(
    (d) =>
      (idQuery != null && (d.leftIndex === idQuery || d.rightIndex === idQuery)) ||
      d.id.toLowerCase().includes(lower) ||
      d.class?.toLowerCase().includes(lower) ||
      d.lines.some((line) => line.text.toLowerCase().includes(lower)),
  );
}

/* ── Misc ──────────────────────────────────────────── */

export function packetAnchor(protocol: string, flow: string, id: string): string {
  return `${protocol}-${flow}-${id.replace(/[:/]/g, '-')}`;
}

export function typeAnchor(name: string): string {
  return `type-${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
}

export function formatId(index: number): string {
  return String(index);
}
