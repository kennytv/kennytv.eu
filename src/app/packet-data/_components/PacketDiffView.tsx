'use client';

import { useState, useEffect, useMemo } from 'react';
import type { VersionPacketData, PacketDiff } from '../_lib/packetDataUtils';
import { computePacketDiff, filterPacketDiffs, PROTOCOL_LABELS, FLOW_LABELS } from '../_lib/packetDataUtils';
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
      diffs = diffs.filter((d) => d.status !== 'changed' || d.lines.some((l) => l.status !== 'same'));
    }
    return diffs;
  }, [allDiffs, query, hideIndexOnly]);

  const changedCount = allDiffs.filter((d) => d.status !== 'unchanged').length;
  const grouped = useMemo(() => groupDiffs(filteredDiffs), [filteredDiffs]);

  return (
    <>
      {/* Controls — sticky so search and version selection stay reachable while scrolling.
          The wrapper carries the stickiness: .card sets position:relative, which
          would override the sticky utility on the same element. */}
      <div className="sticky top-2 z-30 mb-6">
      <div className="card p-4 shadow-lg shadow-black/20 md:p-6">
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
          {grouped.map(({ protocol, flow, diffs }) => (
            <div key={`${protocol}-${flow}`}>
              <h2 className="mb-3 text-sm font-medium text-text-dim uppercase tracking-wider">
                {PROTOCOL_LABELS[protocol] ?? protocol} / {FLOW_LABELS[flow] ?? flow}
              </h2>
              <div className="space-y-4">
                {diffs.map((diff) => (
                  <PacketDiffCard key={`${diff.protocol}-${diff.flow}-${diff.id}`} diff={diff} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

interface GroupedDiff {
  protocol: string;
  flow: string;
  diffs: PacketDiff[];
}

function groupDiffs(diffs: PacketDiff[]): GroupedDiff[] {
  const result: GroupedDiff[] = [];
  for (const diff of diffs) {
    let group = result.find((g) => g.protocol === diff.protocol && g.flow === diff.flow);
    if (!group) {
      group = { protocol: diff.protocol, flow: diff.flow, diffs: [] };
      result.push(group);
    }
    group.diffs.push(diff);
  }
  return result;
}
