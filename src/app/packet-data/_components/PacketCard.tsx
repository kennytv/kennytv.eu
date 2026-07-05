'use client';

import type { PacketInfo } from '../_lib/packetDataUtils';
import { formatId, packetAnchor, parseIdQuery } from '../_lib/packetDataUtils';
import NodeTree from './NodeTree';

interface PacketCardProps {
  packet: PacketInfo;
  protocol: string;
  flow: string;
  onTypeClick?: (typeName: string) => void;
  /** Lowercased search query for highlighting matches in the body. */
  highlight?: string;
}

export default function PacketCard({ packet, protocol, flow, onTypeClick, highlight }: PacketCardProps) {
  const anchor = packetAnchor(protocol, flow, packet.id);
  const idMatched = !!highlight && parseIdQuery(highlight) === packet.index;
  return (
    <div id={anchor} className="card scroll-mt-4 p-4 md:p-6">
      <div className="group mb-3 flex flex-wrap items-center gap-3">
        <h4 className="text-base font-semibold text-text">{packet.id.replace('minecraft:', '')}</h4>
        <span
          className={`rounded-full border px-2 py-0.5 text-xs font-mono ${
            idMatched ? 'border-amber/60 bg-amber/20 text-text' : 'border-border bg-bg-surface text-text-dim'
          }`}
        >
          {formatId(packet.index)}
        </span>
        {packet.class && (
          <span className="text-xs text-text-muted font-mono">{packet.class}</span>
        )}
        <a
          href={`#${anchor}`}
          aria-label="Link to this packet"
          className="text-text-dim opacity-0 transition-opacity group-hover:opacity-100 hover:text-primary"
        >
          #
        </a>
      </div>
      <NodeTree node={packet.body} onTypeClick={onTypeClick} highlight={highlight} />
    </div>
  );
}
