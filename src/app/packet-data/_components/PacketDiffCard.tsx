'use client';

import type { PacketDiff, DiffLineStatus } from '../_lib/packetDataUtils';
import { PROTOCOL_LABELS, FLOW_LABELS, formatId } from '../_lib/packetDataUtils';

interface PacketDiffCardProps {
  diff: PacketDiff;
}

const LINE_STYLES: Record<DiffLineStatus, string> = {
  same: 'text-text-muted',
  added: 'bg-green-500/10 text-green-400',
  removed: 'bg-red-500/10 text-red-400',
};

const LINE_MARKERS: Record<DiffLineStatus, string> = {
  same: ' ',
  added: '+',
  removed: '-',
};

const CARD_BORDER: Record<string, string> = {
  added: 'border-green-500/30',
  removed: 'border-red-500/30',
  changed: '',
  unchanged: '',
};

export default function PacketDiffCard({ diff }: PacketDiffCardProps) {
  const showBody = diff.lines.some((l) => l.status !== 'same');

  return (
    <div className={`card p-4 md:p-6 ${CARD_BORDER[diff.status]}`}>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <h3 className="text-base font-semibold text-text">{diff.id.replace('minecraft:', '')}</h3>
        <StatusBadge status={diff.status} />
        <span className="rounded-full border border-border bg-bg-surface px-2 py-0.5 text-xs text-text-dim">
          {PROTOCOL_LABELS[diff.protocol] ?? diff.protocol} / {FLOW_LABELS[diff.flow] ?? diff.flow}
        </span>
        {diff.class && <span className="text-xs text-text-muted font-mono">{diff.class}</span>}
      </div>

      {diff.leftIndex != null && diff.rightIndex != null && diff.leftIndex !== diff.rightIndex && (
        <p className="mb-2 text-xs text-amber-400">
          Index changed: {formatId(diff.leftIndex)} → {formatId(diff.rightIndex)}
        </p>
      )}

      {showBody ? (
        <div className="overflow-x-auto rounded-lg border border-border/60 bg-bg-surface/40">
          <pre className="text-xs leading-5 p-2">
            {diff.lines.map((line, i) => (
              <div key={i} className={`px-1 ${LINE_STYLES[line.status]}`}>
                {LINE_MARKERS[line.status]} {line.text || ' '}
              </div>
            ))}
          </pre>
        </div>
      ) : (
        <p className="text-sm text-text-dim">Structure unchanged.</p>
      )}
    </div>
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
