'use client';

import type { EntityDiff, DiffField, FieldDiffStatus } from '../_lib/entityDataUtils';

interface DiffCardProps {
  diff: EntityDiff;
  leftVersion: string;
  rightVersion: string;
  hideIndexOnly: boolean;
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

export default function DiffCard({ diff, leftVersion, rightVersion, hideIndexOnly }: DiffCardProps) {
  const fields = hideIndexOnly
    ? diff.fields.filter((f) => !isIndexOnlyChange(f))
    : diff.fields;

  return (
    <div
      id={`diff-${diff.name.toLowerCase().replace(/\s+/g, '-')}`}
      className={`card p-4 md:p-6 ${ENTITY_BG[diff.status]}`}
    >
      <div className="mb-3 flex items-center gap-3">
        <h3 className="text-base font-semibold text-text">{diff.name}</h3>
        <StatusBadge status={diff.status} />
      </div>

      {/* Super class change */}
      {diff.leftSuperClass !== diff.rightSuperClass && (
        <p className="mb-2 text-xs text-amber-400">
          Extends changed: {diff.leftSuperClass ?? 'None'} → {diff.rightSuperClass ?? 'None'}
        </p>
      )}

      {fields.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-text-dim">
                <th className="py-2 pr-2 font-medium w-8"></th>
                <th className="py-2 pr-4 font-medium">{leftVersion} Index</th>
                <th className="py-2 pr-4 font-medium">{rightVersion} Index</th>
                <th className="py-2 pr-4 font-medium">Data Type</th>
                <th className="py-2 font-medium">Field Name</th>
              </tr>
            </thead>
            <tbody>
              {fields.map((field, i) => (
                <DiffRow key={i} field={field} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-sm text-text-dim">No entity data fields.</p>
      )}
    </div>
  );
}

/** A field where only the index shifted but name and type stayed the same */
function isIndexOnlyChange(field: DiffField): boolean {
  if (field.status !== 'changed' || !field.left || !field.right) return false;
  return field.left.dataType === field.right.dataType
    && field.left.fieldName === field.right.fieldName;
}

function DiffRow({ field }: { field: DiffField }) {
  const style = STATUS_STYLES[field.status];
  const indicator = field.status === 'added' ? '+' : field.status === 'removed' ? '-' : field.status === 'changed' ? '~' : '';

  // For changed data types, show both
  const dataType = (() => {
    if (field.status === 'changed' && field.left && field.right && field.left.dataType !== field.right.dataType) {
      return (
        <>
          <span className="text-red-400 line-through">{field.left.dataType}</span>
          {' → '}
          <span className="text-green-400">{field.right.dataType}</span>
        </>
      );
    }
    return field.right?.dataType ?? field.left?.dataType ?? '';
  })();

  return (
    <tr className={`border-b border-border/50 ${style}`}>
      <td className="py-1.5 pr-2 font-mono font-bold">{indicator}</td>
      <td className="py-1.5 pr-4 font-mono text-text-muted">
        {field.left?.index ?? ''}
      </td>
      <td className="py-1.5 pr-4 font-mono text-text-muted">
        {field.right?.index ?? ''}
      </td>
      <td className="py-1.5 pr-4 font-mono text-amber">{dataType}</td>
      <td className="py-1.5 font-mono text-text">
        {field.right?.fieldName ?? field.left?.fieldName ?? ''}
      </td>
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
