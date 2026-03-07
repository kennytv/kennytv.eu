'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import type { VersionPacketData, FilteredPackets } from '../_lib/packetDataUtils';
import {
  filterPackets,
  getTotalPacketCount,
  getFilteredCount,
  PROTOCOL_LABELS,
  DIRECTION_LABELS,
} from '../_lib/packetDataUtils';
import PacketTable from './PacketTable';
import TypesSection from './TypesSection';
import PacketSearch from './PacketSearch';
import VersionSelector from './VersionSelector';

interface PacketBrowseViewProps {
  versions: string[];
  fetchVersion: (version: string) => Promise<VersionPacketData>;
  onCompare: () => void;
}

export default function PacketBrowseView({ versions, fetchVersion, onCompare }: PacketBrowseViewProps) {
  const [version, setVersion] = useState(versions[versions.length - 1]);
  const [data, setData] = useState<VersionPacketData | null>(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');

  useEffect(() => {
    setLoading(true);
    fetchVersion(version)
      .then(setData)
      .finally(() => setLoading(false));
  }, [version, fetchVersion]);

  const sections = useMemo(() => {
    if (!data) return [];
    return filterPackets(data, query);
  }, [data, query]);

  const totalCount = data ? getTotalPacketCount(data) : 0;
  const filteredCount = getFilteredCount(sections);

  const scrollToPacket = useCallback((name: string) => {
    const anchor = name.toLowerCase().replace(/[.\s]+/g, '-');
    const el = document.getElementById(anchor);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

  if (loading || !data) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  // Group sections by protocol for display
  const byProtocol = groupByProtocol(sections);

  return (
    <>
      {/* Controls */}
      <div className="card mb-6 p-4 md:p-6">
        <div className="mb-4 flex flex-wrap items-center gap-4">
          <VersionSelector versions={versions} selected={version} onChange={setVersion} label="Version:" />
          <button
            type="button"
            onClick={onCompare}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white
              shadow-md shadow-primary/25 hover:bg-primary-dark transition-colors"
          >
            Compare versions
          </button>
        </div>
        <PacketSearch
          query={query}
          onChange={setQuery}
          resultCount={filteredCount}
          totalCount={totalCount}
        />
      </div>

      {/* Packet sections grouped by protocol */}
      {filteredCount === 0 ? (
        <div className="card p-8 text-center text-text-dim">
          No packets match your search.
        </div>
      ) : (
        <div className="space-y-8">
          {byProtocol.map(({ protocol, directions }) => (
            <div key={protocol}>
              <h2 className="mb-4 text-lg font-semibold text-text">
                {PROTOCOL_LABELS[protocol] ?? protocol}
              </h2>
              <div className="space-y-6">
                {directions.map(({ direction, packets }) => (
                  <div key={`${protocol}-${direction}`}>
                    <h3 className="mb-3 text-sm font-medium text-text-dim uppercase tracking-wider">
                      {DIRECTION_LABELS[direction]}
                      <span className="ml-2 text-text-muted font-normal normal-case tracking-normal">
                        ({packets.length} {packets.length === 1 ? 'packet' : 'packets'})
                      </span>
                    </h3>
                    <div className="space-y-3">
                      {packets.map((packet) => (
                        <PacketTable
                          key={packet.name}
                          packet={packet}
                          types={data.types}
                          onTypeClick={scrollToPacket}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Base types reference section */}
      {data.types && Object.keys(data.types).length > 0 && (
        <TypesSection types={data.types} />
      )}
    </>
  );
}

interface ProtocolGroup {
  protocol: string;
  directions: { direction: 'CLIENTBOUND' | 'SERVERBOUND'; packets: FilteredPackets['packets'] }[];
}

function groupByProtocol(sections: FilteredPackets[]): ProtocolGroup[] {
  const map = new Map<string, Map<string, FilteredPackets['packets']>>();

  for (const section of sections) {
    if (!map.has(section.protocol)) {
      map.set(section.protocol, new Map());
    }
    map.get(section.protocol)!.set(section.direction, section.packets);
  }

  const result: ProtocolGroup[] = [];
  for (const [protocol, dirMap] of map) {
    const directions: ProtocolGroup['directions'] = [];
    for (const [direction, packets] of dirMap) {
      directions.push({ direction: direction as 'CLIENTBOUND' | 'SERVERBOUND', packets });
    }
    result.push({ protocol, directions });
  }

  return result;
}
