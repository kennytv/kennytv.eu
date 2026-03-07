'use client';

import type { PacketInfo, CustomType } from '../_lib/packetDataUtils';
import { getReferencedType } from '../_lib/packetDataUtils';

interface PacketTableProps {
  packet: PacketInfo;
  types?: Record<string, CustomType>;
  onTypeClick?: (typeName: string) => void;
}

export default function PacketTable({ packet, types, onTypeClick }: PacketTableProps) {
  const hasLimits = packet.fields.some((f) => f.limit != null);

  return (
    <div id={toAnchor(packet.name)} className="card p-4 md:p-6">
      <div className="mb-3 flex items-center gap-3">
        <h4 className="text-base font-semibold text-text">{packet.name}</h4>
        <span className="rounded-full border border-border bg-bg-surface px-2 py-0.5 text-xs text-text-dim font-mono">
          0x{packet.index.toString(16).toUpperCase().padStart(2, '0')}
        </span>
      </div>

      {packet.fields.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-text-dim">
                <th className="py-2 pr-4 font-medium">Field Name</th>
                <th className="py-2 pr-4 font-medium">Type</th>
                {hasLimits && <th className="py-2 font-medium">Limit</th>}
              </tr>
            </thead>
            <tbody>
              {packet.fields.map((field, i) => {
                const refType = getReferencedType(field.type, types);
                return (
                  <tr key={`${field.name}-${i}`} className="border-b border-border/50">
                    <td className="py-1.5 pr-4 font-mono text-text">{field.name}</td>
                    <td className="py-1.5 pr-4 font-mono text-amber">
                      {refType ? (
                        <TypeLink type={field.type} refType={refType} onClick={onTypeClick} />
                      ) : (
                        field.type
                      )}
                    </td>
                    {hasLimits && (
                      <td className="py-1.5 font-mono text-text-muted">
                        {field.limit ?? ''}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-sm text-text-dim">Empty packet.</p>
      )}
    </div>
  );
}

function TypeLink({
  type,
  refType,
  onClick,
}: {
  type: string;
  refType: string;
  onClick?: (name: string) => void;
}) {
  // If the type is a wrapper like List<Entry>, render the wrapper part normally
  // and the inner type as a link
  if (type !== refType) {
    const idx = type.indexOf(refType);
    const before = type.slice(0, idx);
    const after = type.slice(idx + refType.length);
    return (
      <>
        {before}
        <button
          type="button"
          onClick={() => onClick?.(refType)}
          className="text-primary hover:text-primary-light transition-colors underline"
        >
          {refType}
        </button>
        {after}
      </>
    );
  }

  return (
    <button
      type="button"
      onClick={() => onClick?.(refType)}
      className="text-primary hover:text-primary-light transition-colors underline"
    >
      {refType}
    </button>
  );
}

function toAnchor(name: string): string {
  return name.toLowerCase().replace(/[.\s]+/g, '-');
}
