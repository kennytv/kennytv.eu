/* ── Types ──────────────────────────────────────────── */

export interface PacketField {
  name: string;
  type: string;
  limit?: number;
}

export interface PacketInfo {
  name: string;
  index: number;
  fields: PacketField[];
}

export interface CustomType {
  fields: PacketField[];
}

export interface ProtocolPackets {
  CLIENTBOUND: PacketInfo[];
  SERVERBOUND: PacketInfo[];
}

export interface VersionPacketData {
  packets: Record<string, ProtocolPackets>;
  types?: Record<string, CustomType>;
}

export interface PacketDataIndex {
  versions: string[];
}

export type Direction = 'CLIENTBOUND' | 'SERVERBOUND';

export const PROTOCOL_ORDER = ['HANDSHAKE', 'STATUS', 'LOGIN', 'CONFIGURATION', 'PLAY'] as const;
export type Protocol = (typeof PROTOCOL_ORDER)[number];

export const DIRECTION_LABELS: Record<Direction, string> = {
  CLIENTBOUND: 'Clientbound',
  SERVERBOUND: 'Serverbound',
};

export const PROTOCOL_LABELS: Record<string, string> = {
  HANDSHAKE: 'Handshake',
  STATUS: 'Status',
  LOGIN: 'Login',
  CONFIGURATION: 'Configuration',
  PLAY: 'Play',
};

/* ── Search ────────────────────────────────────────── */

export interface FilteredPackets {
  protocol: string;
  direction: Direction;
  packets: PacketInfo[];
}

/** Get total packet count across all protocols and directions */
export function getTotalPacketCount(data: VersionPacketData): number {
  let count = 0;
  for (const protocol of Object.values(data.packets)) {
    count += protocol.CLIENTBOUND.length + protocol.SERVERBOUND.length;
  }
  return count;
}

/** Filter packets across all protocols/directions by search query */
export function filterPackets(
  data: VersionPacketData,
  query: string,
): FilteredPackets[] {
  const lower = query.toLowerCase().trim();
  const results: FilteredPackets[] = [];

  for (const protocol of PROTOCOL_ORDER) {
    const protocolData = data.packets[protocol];
    if (!protocolData) continue;

    for (const direction of ['CLIENTBOUND', 'SERVERBOUND'] as Direction[]) {
      const packets = protocolData[direction];
      if (!packets || packets.length === 0) continue;

      const filtered = lower
        ? packets.filter((p) => {
            if (p.name.toLowerCase().includes(lower)) return true;
            return p.fields.some(
              (f) =>
                f.name.toLowerCase().includes(lower) ||
                f.type.toLowerCase().includes(lower),
            );
          })
        : packets;

      if (filtered.length > 0) {
        results.push({ protocol, direction, packets: filtered });
      }
    }
  }

  return results;
}

/** Get the count of filtered packets */
export function getFilteredCount(sections: FilteredPackets[]): number {
  return sections.reduce((sum, s) => sum + s.packets.length, 0);
}

/* ── Diff Types ────────────────────────────────────── */

export type FieldDiffStatus = 'unchanged' | 'added' | 'removed' | 'changed';

export interface PacketFieldDiff {
  status: FieldDiffStatus;
  left?: PacketField;
  right?: PacketField;
}

export type PacketDiffStatus = 'unchanged' | 'added' | 'removed' | 'changed';

export interface PacketDiff {
  status: PacketDiffStatus;
  name: string;
  protocol: string;
  direction: Direction;
  leftIndex?: number;
  rightIndex?: number;
  fields: PacketFieldDiff[];
}

/* ── Diff Computation ──────────────────────────────── */

export function computePacketDiff(
  left: VersionPacketData,
  right: VersionPacketData,
): PacketDiff[] {
  const diffs: PacketDiff[] = [];

  for (const protocol of PROTOCOL_ORDER) {
    const leftProto = left.packets[protocol];
    const rightProto = right.packets[protocol];

    for (const direction of ['CLIENTBOUND', 'SERVERBOUND'] as Direction[]) {
      const leftPackets = leftProto?.[direction] ?? [];
      const rightPackets = rightProto?.[direction] ?? [];

      const leftByName = new Map(leftPackets.map((p) => [p.name, p]));
      const rightByName = new Map(rightPackets.map((p) => [p.name, p]));
      const allNames = new Set([...leftByName.keys(), ...rightByName.keys()]);

      for (const name of Array.from(allNames).sort()) {
        const l = leftByName.get(name);
        const r = rightByName.get(name);

        if (!l) {
          diffs.push({
            status: 'added',
            name,
            protocol,
            direction,
            rightIndex: r!.index,
            fields: r!.fields.map((f) => ({ status: 'added', right: f })),
          });
        } else if (!r) {
          diffs.push({
            status: 'removed',
            name,
            protocol,
            direction,
            leftIndex: l.index,
            fields: l.fields.map((f) => ({ status: 'removed', left: f })),
          });
        } else {
          const fields = diffPacketFields(l.fields, r.fields);
          const hasChanges =
            fields.some((f) => f.status !== 'unchanged') ||
            l.index !== r.index;

          diffs.push({
            status: hasChanges ? 'changed' : 'unchanged',
            name,
            protocol,
            direction,
            leftIndex: l.index,
            rightIndex: r.index,
            fields,
          });
        }
      }
    }
  }

  return diffs;
}

function diffPacketFields(
  leftFields: PacketField[],
  rightFields: PacketField[],
): PacketFieldDiff[] {
  const result: PacketFieldDiff[] = [];

  const leftByName = new Map(leftFields.map((f) => [f.name, f]));
  const rightByName = new Map(rightFields.map((f) => [f.name, f]));
  const allFieldNames = new Set([...leftByName.keys(), ...rightByName.keys()]);

  for (const fieldName of allFieldNames) {
    const l = leftByName.get(fieldName);
    const r = rightByName.get(fieldName);

    if (!l) {
      result.push({ status: 'added', right: r });
    } else if (!r) {
      result.push({ status: 'removed', left: l });
    } else if (l.type !== r.type || l.limit !== r.limit) {
      result.push({ status: 'changed', left: l, right: r });
    } else {
      result.push({ status: 'unchanged', left: l, right: r });
    }
  }

  return result;
}

/** Filter diffs by search query */
export function filterPacketDiffs(
  diffs: PacketDiff[],
  query: string,
): PacketDiff[] {
  if (!query.trim()) return diffs;
  const lower = query.toLowerCase();
  return diffs.filter((d) => {
    if (d.name.toLowerCase().includes(lower)) return true;
    return d.fields.some((f) => {
      const name = f.left?.name ?? f.right?.name ?? '';
      const type = f.left?.type ?? f.right?.type ?? '';
      return name.toLowerCase().includes(lower) || type.toLowerCase().includes(lower);
    });
  });
}

/* ── Type Reference Helpers ────────────────────────── */

/** Check if a field type references a known custom type */
export function getReferencedType(fieldType: string, types: Record<string, CustomType> | undefined): string | null {
  if (!types) return null;

  // Direct match
  if (types[fieldType]) return fieldType;

  // Match inside wrappers like Optional<Entry>, List<Entry>
  const inner = fieldType.match(/(?:Optional|List|Collection|WeightedList|HolderSet|Holder)<(.+?)>$/);
  if (inner && types[inner[1]]) return inner[1];

  return null;
}
