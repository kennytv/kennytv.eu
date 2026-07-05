'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import type { VersionPacketData } from '../_lib/packetDataUtils';
import {
  filterPackets,
  filterTypes,
  getTotalPacketCount,
  getFilteredCount,
  typeAnchor,
  PROTOCOL_LABELS,
  FLOW_LABELS,
} from '../_lib/packetDataUtils';
import PacketCard from './PacketCard';
import TypesSection from './TypesSection';
import TypePanel from './TypePanel';
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
  const [openType, setOpenType] = useState<string | null>(null);
  const [panelStack, setPanelStack] = useState<string[]>([]);

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

  const lowerQuery = query.toLowerCase().trim();

  const visibleTypes = useMemo(() => {
    if (!data?.types) return {};
    return filterTypes(data.types, query);
  }, [data, query]);

  const totalCount = data ? getTotalPacketCount(data) : 0;
  const filteredCount = getFilteredCount(sections);

  // Deep links (#type-...) open the panel; packet anchors are handled natively.
  useEffect(() => {
    const onHashChange = () => {
      const hash = window.location.hash.slice(1);
      if (hash.startsWith('type-') && data?.types) {
        const name = Object.keys(data.types).find((t) => typeAnchor(t) === hash);
        if (name) {
          setOpenType(name);
          setPanelStack([name]);
        }
      }
    };
    onHashChange();
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, [data]);

  /** Type links open the side panel; nested links push onto its breadcrumb trail. */
  const openTypeInPanel = useCallback((name: string) => {
    setPanelStack((stack) => (stack[stack.length - 1] === name ? stack : [...stack, name]));
  }, []);

  if (loading || !data) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <>
      {/* Controls — sticky so search and version stay reachable while scrolling.
          The wrapper carries the stickiness: .card sets position:relative, which
          would override the sticky utility on the same element. */}
      <div className="sticky top-2 z-30 mb-6">
      <div className="card p-4 shadow-lg shadow-black/20 md:p-6">
        <div className="mb-4 flex flex-wrap items-center gap-4">
          <VersionSelector versions={versions} selected={version} onChange={setVersion} label="Version:" />
          {data.protocolVersion != null && (
            <span className="text-sm text-text-dim">protocol {data.protocolVersion}</span>
          )}
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
      </div>

      {/* Packet sections grouped by protocol */}
      {filteredCount === 0 ? (
        <div className="card p-8 text-center text-text-dim">No packets match your search.</div>
      ) : (
        <div className="space-y-8">
          {groupByProtocol(sections).map(({ protocol, flows }) => (
            <div key={protocol}>
              <h2 className="mb-4 text-lg font-semibold text-text">
                {PROTOCOL_LABELS[protocol] ?? protocol}
              </h2>
              <div className="space-y-6">
                {flows.map(({ flow, packets }) => (
                  <div key={`${protocol}-${flow}`}>
                    <h3 className="mb-3 text-sm font-medium text-text-dim uppercase tracking-wider">
                      {FLOW_LABELS[flow] ?? flow}
                      <span className="ml-2 text-text-muted font-normal normal-case tracking-normal">
                        ({packets.length} {packets.length === 1 ? 'packet' : 'packets'})
                      </span>
                    </h3>
                    <div className="space-y-3">
                      {packets.map((packet) => (
                        <PacketCard
                          key={packet.id}
                          packet={packet}
                          protocol={protocol}
                          flow={flow}
                          onTypeClick={openTypeInPanel}
                          highlight={lowerQuery}
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

      {/* Shared types reference section — filtered by the same search query */}
      {Object.keys(visibleTypes).length > 0 && (
        <TypesSection
          types={visibleTypes}
          onTypeClick={openTypeInPanel}
          openType={openType}
          highlight={lowerQuery}
        />
      )}

      {/* Side panel for type browsing */}
      {panelStack.length > 0 && data.types && (
        <TypePanel
          stack={panelStack}
          types={data.types}
          onOpen={openTypeInPanel}
          onJumpTo={(index) => setPanelStack((stack) => stack.slice(0, index + 1))}
          onClose={() => setPanelStack([])}
        />
      )}
    </>
  );
}

interface ProtocolGroup {
  protocol: string;
  flows: { flow: string; packets: VersionPacketData['protocols'][string][string] }[];
}

function groupByProtocol(
  sections: { protocol: string; flow: string; packets: VersionPacketData['protocols'][string][string] }[],
): ProtocolGroup[] {
  const result: ProtocolGroup[] = [];
  for (const section of sections) {
    let group = result.find((g) => g.protocol === section.protocol);
    if (!group) {
      group = { protocol: section.protocol, flows: [] };
      result.push(group);
    }
    group.flows.push({ flow: section.flow, packets: section.packets });
  }
  return result;
}
