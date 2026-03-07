'use client';

import type { PacketDiff, PacketFieldDiff, FieldDiffStatus } from '../_lib/packetDataUtils';
import { PROTOCOL_LABELS, DIRECTION_LABELS } from '../_lib/packetDataUtils';

interface PacketDiffCardProps {
  diff: PacketDiff;
  leftVersion: string;
  rightVersion: string;
}

const STATUS_STYLES: Record<FieldDiffStatus, string> = {
  unchanged: '',
  added: 'bg-green-500/10 text-green-400',
  removed: 'bg-red-500/10 text-red-400',
  changed: 'bg-amber-500/10 text-amber-400',
};

const ENTITY_BG: Record<string, string> = {
  added: 'border-green-500/30',
  removed: 'border-red-500/30',
  changed: '',
  unchanged: '',
};

export default function PacketDiffCard({ diff, leftVersion, rightVersion }: PacketDiffCardProps) {
  return (
    <div
      id={`diff-${diff.name.toLowerCase().replace(/[.\s]+/g, '-')}`}
      className={`card p-4 md:p-6 ${ENTITY_BG[diff.status]}`}
    >
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <h3 className="text-base font-semibold text-text">{diff.name}</h3>
        <StatusBadge status={diff.status} />
        <span className="rounded-full border border-border bg-bg-surface px-2 py-0.5 text-xs text-text-dim">
          {PROTOCOL_LABELS[diff.protocol] ?? diff.protocol} / {DIRECTION_LABELS[diff.direction]}
        </span>
      </div>

      {/* Index change */}
      {diff.leftIndex != null && diff.rightIndex != null && diff.leftIndex !== diff.rightIndex && (
        <p className="mb-2 text-xs text-amber-400">
          Index changed: 0x{diff.leftIndex.toString(16).toUpperCase().padStart(2, '0')} →{' '}
          0x{diff.rightIndex.toString(16).toUpperCase().padStart(2, '0')}
        </p>
      )}

      {diff.fields.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-text-dim">
                <th className="py-2 pr-2 font-medium w-8"></th>
                <th className="py-2 pr-4 font-medium">Field Name</th>
                <th className="py-2 pr-4 font-medium">{leftVersion} Type</th>
                <th className="py-2 font-medium">{rightVersion} Type</th>
              </tr>
            </thead>
            <tbody>
              {diff.fields.map((field, i) => (
                <DiffRow key={i} field={field} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-sm text-text-dim">
          {diff.status === 'added' || diff.status === 'removed'
            ? 'Empty packet.'
            : 'No field changes.'}
        </p>
      )}
    </div>
  );
}

function DiffRow({ field }: { field: PacketFieldDiff }) {
  const style = STATUS_STYLES[field.status];
  const indicator =
    field.status === 'added' ? '+' : field.status === 'removed' ? '-' : field.status === 'changed' ? '~' : '';

  const leftType = field.left?.type ?? '';
  const rightType = field.right?.type ?? '';
  const fieldName = field.right?.name ?? field.left?.name ?? '';

  // For changed types, show both with visual distinction
  const typeDisplay = (() => {
    if (field.status === 'changed' && field.left && field.right && field.left.type !== field.right.type) {
      return {
        left: <span className="text-red-400 line-through">{field.left.type}</span>,
        right: <span className="text-green-400">{field.right.type}</span>,
      };
    }
    return {
      left: <span className="text-amber">{leftType}</span>,
      right: <span className="text-amber">{rightType}</span>,
    };
  })();

  return (
    <tr className={`border-b border-border/50 ${style}`}>
      <td className="py-1.5 pr-2 font-mono font-bold">{indicator}</td>
      <td className="py-1.5 pr-4 font-mono text-text">{fieldName}</td>
      <td className="py-1.5 pr-4 font-mono">{typeDisplay.left}</td>
      <td className="py-1.5 font-mono">{typeDisplay.right}</td>
    </tr>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    added: 'bg-green-500/20 text-green-400 border-green-500/30',
    removed: 'bg-red-500/20 text-red-400 border-red-500/30',
    changed: 'bg-amber-500/20 text-amber-400 border-amber-500/30',
    unchanged: 'bg-bg-surface text-text-dim border-border',
  };

  return (
    <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${styles[status] ?? ''}`}>
      {status}
    </span>
  );
}
