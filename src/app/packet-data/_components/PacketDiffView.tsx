'use client';

import { useState, useEffect, useMemo } from 'react';
import type { VersionPacketData, PacketDiff } from '../_lib/packetDataUtils';
import { computePacketDiff, filterPacketDiffs, PROTOCOL_LABELS, DIRECTION_LABELS } from '../_lib/packetDataUtils';
import VersionSelector from './VersionSelector';
import PacketSearch from './PacketSearch';
import PacketDiffCard from './PacketDiffCard';

interface PacketDiffViewProps {
  versions: string[];
  fetchVersion: (version: string) => Promise<VersionPacketData>;
  onBack: () => void;
}

export default function PacketDiffView({ versions, fetchVersion, onBack }: PacketDiffViewProps) {
  const [leftVersion, setLeftVersion] = useState(
    versions.length >= 2 ? versions[versions.length - 2] : versions[0],
  );
  const [rightVersion, setRightVersion] = useState(versions[versions.length - 1]);
  const [leftData, setLeftData] = useState<VersionPacketData | null>(null);
  const [rightData, setRightData] = useState<VersionPacketData | null>(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [hideIndexOnly, setHideIndexOnly] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([fetchVersion(leftVersion), fetchVersion(rightVersion)])
      .then(([left, right]) => {
        setLeftData(left);
        setRightData(right);
      })
      .finally(() => setLoading(false));
  }, [leftVersion, rightVersion, fetchVersion]);

  const allDiffs = useMemo(() => {
    if (!leftData || !rightData) return [];
    return computePacketDiff(leftData, rightData);
  }, [leftData, rightData]);

  const filteredDiffs = useMemo(() => {
    let diffs = filterPacketDiffs(allDiffs, query);
    diffs = diffs.filter((d) => d.status !== 'unchanged');
    if (hideIndexOnly) {
      diffs = diffs.filter((d) => hasNonIndexChanges(d));
    }
    return diffs;
  }, [allDiffs, query, hideIndexOnly]);

  const changedCount = allDiffs.filter((d) => d.status !== 'unchanged').length;

  // Group diffs by protocol > direction
  const grouped = useMemo(() => groupDiffsByProtocol(filteredDiffs), [filteredDiffs]);

  return (
    <>
      {/* Controls */}
      <div className="card mb-6 p-4 md:p-6">
        <div className="mb-4 flex flex-wrap items-center gap-4">
          <button
            type="button"
            onClick={onBack}
            className="rounded-lg border border-border bg-bg-surface px-4 py-2 text-sm font-medium
              text-text-muted hover:border-border-light hover:text-text transition-colors"
          >
            Back to browse
          </button>
          <VersionSelector versions={versions} selected={leftVersion} onChange={setLeftVersion} label="From:" />
          <span className="text-text-dim">vs</span>
          <VersionSelector versions={versions} selected={rightVersion} onChange={setRightVersion} label="To:" />
        </div>

        <div className="mb-4">
          <PacketSearch
            query={query}
            onChange={setQuery}
            resultCount={filteredDiffs.length}
            totalCount={allDiffs.length}
          />
        </div>

        <div className="flex flex-wrap items-center gap-4 border-t border-border pt-4 text-sm">
          <label className="inline-flex cursor-pointer items-center gap-2 text-text-muted">
            <input
              type="checkbox"
              checked={hideIndexOnly}
              onChange={(e) => setHideIndexOnly(e.target.checked)}
              className="h-4 w-4 accent-primary"
            />
            Hide index-only changes
          </label>
          <span className="text-text-dim">
            {changedCount} {changedCount === 1 ? 'packet' : 'packets'} changed
          </span>
        </div>
      </div>

      {/* Diff results */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : leftVersion === rightVersion ? (
        <div className="card p-8 text-center text-text-dim">
          Select two different versions to compare.
        </div>
      ) : filteredDiffs.length === 0 ? (
        <div className="card p-8 text-center text-text-dim">
          {query ? 'No matching packets found.' : 'No differences between these versions.'}
        </div>
      ) : (
        <div className="space-y-8">
          {grouped.map(({ protocol, direction, diffs }) => (
            <div key={`${protocol}-${direction}`}>
              <h2 className="mb-3 text-sm font-medium text-text-dim uppercase tracking-wider">
                {PROTOCOL_LABELS[protocol] ?? protocol} / {DIRECTION_LABELS[direction]}
              </h2>
              <div className="space-y-4">
                {diffs.map((diff) => (
                  <PacketDiffCard
                    key={diff.name}
                    diff={diff}
                    leftVersion={leftVersion}
                    rightVersion={rightVersion}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

/** Check if a diff has any changes beyond pure index shifts */
function hasNonIndexChanges(diff: PacketDiff): boolean {
  if (diff.status === 'added' || diff.status === 'removed') return true;
  return diff.fields.some((f) => f.status !== 'unchanged');
}

interface GroupedDiff {
  protocol: string;
  direction: 'CLIENTBOUND' | 'SERVERBOUND';
  diffs: PacketDiff[];
}

function groupDiffsByProtocol(diffs: PacketDiff[]): GroupedDiff[] {
  const map = new Map<string, PacketDiff[]>();

  for (const diff of diffs) {
    const key = `${diff.protocol}-${diff.direction}`;
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(diff);
  }

  const result: GroupedDiff[] = [];
  for (const [key, groupDiffs] of map) {
    const [protocol, direction] = key.split('-') as [string, 'CLIENTBOUND' | 'SERVERBOUND'];
    result.push({ protocol, direction, diffs: groupDiffs });
  }

  return result;
}
