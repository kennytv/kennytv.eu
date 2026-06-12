'use client';

import { useState, useMemo, useCallback, useEffect } from 'react';
import { ArrowLeftRight } from 'lucide-react';
import type { VersionEntities, EntityDiff } from '../_lib/entityDataUtils';
import { computeDiff, filterDiffs } from '../_lib/entityDataUtils';
import { getUrlParams, updateUrlParam } from '../_lib/useUrlState';
import VersionSelector from './VersionSelector';
import EntitySearch from './EntitySearch';
import DiffCard from './DiffCard';
import BackToTop from './BackToTop';

interface EntityDiffViewProps {
  versions: string[];
  leftVersion: string;
  rightVersion: string;
  leftEntities: VersionEntities | null;
  rightEntities: VersionEntities | null;
  leftLoading: boolean;
  rightLoading: boolean;
  onLeftVersionChange: (v: string) => void;
  onRightVersionChange: (v: string) => void;
  onBack: () => void;
}

export default function EntityDiffView({
  versions, leftVersion, rightVersion,
  leftEntities, rightEntities, leftLoading, rightLoading,
  onLeftVersionChange, onRightVersionChange, onBack,
}: EntityDiffViewProps) {
  const [query, setQuery] = useState(() => getUrlParams().get('q') ?? '');
  const [hideIndexOnly, setHideIndexOnly] = useState(true);

  // Persist the search query in the URL (debounced to keep replaceState calls low)
  useEffect(() => {
    const timeout = window.setTimeout(() => updateUrlParam('q', query.trim() ? query : null), 300);
    return () => window.clearTimeout(timeout);
  }, [query]);

  const setLeftVersion = useCallback((v: string) => {
    onLeftVersionChange(v);
    updateUrlParam('from', v);
  }, [onLeftVersionChange]);

  const setRightVersion = useCallback((v: string) => {
    onRightVersionChange(v);
    updateUrlParam('to', v);
  }, [onRightVersionChange]);

  const swapVersions = useCallback(() => {
    setLeftVersion(rightVersion);
    setRightVersion(leftVersion);
  }, [leftVersion, rightVersion, setLeftVersion, setRightVersion]);

  const bothLoaded = !!(leftEntities && rightEntities);
  const eitherLoading = leftLoading || rightLoading;

  const allDiffs = useMemo(
    () => bothLoaded ? computeDiff(leftEntities!, rightEntities!) : [],
    [leftEntities, rightEntities, bothLoaded],
  );

  const filteredDiffs = useMemo(() => {
    let diffs = filterDiffs(allDiffs, query);
    diffs = diffs.filter((d) => d.status !== 'unchanged');
    if (hideIndexOnly) {
      diffs = diffs.filter((d) => hasNonIndexChanges(d));
    }
    return diffs;
  }, [allDiffs, query, hideIndexOnly]);

  const counts = useMemo(() => {
    const c = { added: 0, removed: 0, changed: 0 };
    for (const d of allDiffs) {
      if (d.status !== 'unchanged') c[d.status]++;
    }
    return c;
  }, [allDiffs]);
  const changedCount = counts.added + counts.removed + counts.changed;

  return (
    <>
      {/* Controls (sticky on desktop so search and versions stay reachable) */}
      <div className="mb-6 md:sticky md:top-2 md:z-20">
        <div className="card p-4">
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={onBack}
              className="rounded-lg border border-border bg-bg-surface px-4 py-2 text-sm font-medium
                text-text-muted hover:border-border-light hover:text-text transition-colors"
            >
              Back to browse
            </button>
            <VersionSelector versions={versions} selected={leftVersion} onChange={setLeftVersion} label="From:" />
            <button
              type="button"
              onClick={swapVersions}
              aria-label="Swap versions"
              title="Swap versions"
              className="rounded-lg border border-border bg-bg-surface p-2 text-text-muted
                hover:border-border-light hover:text-text transition-colors"
            >
              <ArrowLeftRight className="h-4 w-4" />
            </button>
            <VersionSelector versions={versions} selected={rightVersion} onChange={setRightVersion} label="To:" />
            <div className="min-w-[220px] flex-1">
              <EntitySearch
                query={query}
                onChange={setQuery}
                resultCount={filteredDiffs.length}
                totalCount={changedCount}
              />
            </div>
          </div>

          <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
            <label className="inline-flex cursor-pointer items-center gap-2 text-text-muted">
              <input
                type="checkbox"
                checked={hideIndexOnly}
                onChange={(e) => setHideIndexOnly(e.target.checked)}
                className="h-4 w-4 accent-primary"
              />
              Hide index-only changes
            </label>
            <span className="flex items-center gap-1.5 text-xs">
              <span className="text-green-400">{counts.added} added</span>
              <span className="text-text-dim">·</span>
              <span className="text-red-400">{counts.removed} removed</span>
              <span className="text-text-dim">·</span>
              <span className="text-amber-400">{counts.changed} changed</span>
            </span>
          </div>
        </div>
      </div>

      {/* Initial loading spinner (no data yet) */}
      {eitherLoading && !bothLoaded && (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-3 border-primary border-t-transparent" />
        </div>
      )}

      {/* Diff cards stay visible (dimmed) while a version loads */}
      {bothLoaded && (
        leftVersion === rightVersion ? (
          <div className="card p-8 text-center text-text-dim">
            Select two different versions to compare.
          </div>
        ) : (
          <>
            <div className={`transition-opacity duration-200 ${eitherLoading ? 'pointer-events-none opacity-40' : ''}`}>
              {filteredDiffs.length === 0 ? (
                <div className="card p-8 text-center text-text-dim">
                  {query ? 'No matching entities found.' : 'No differences between these versions.'}
                </div>
              ) : (
                <div className="space-y-4">
                  {filteredDiffs.map((diff) => (
                    <DiffCard
                      key={diff.name}
                      diff={diff}
                      leftVersion={leftVersion}
                      rightVersion={rightVersion}
                      hideIndexOnly={hideIndexOnly}
                    />
                  ))}
                </div>
              )}
            </div>

            <BackToTop />
          </>
        )
      )}
    </>
  );
}

/** Check if a diff has any changes beyond pure index shifts */
function hasNonIndexChanges(diff: EntityDiff): boolean {
  if (diff.status === 'added' || diff.status === 'removed') return true;
  if (diff.leftSuperClass !== diff.rightSuperClass) return true;
  return diff.fields.some((f) => {
    if (f.status === 'added' || f.status === 'removed') return true;
    if (f.status === 'changed' && f.left && f.right) {
      // A field that only changed index is index-only
      return f.left.dataType !== f.right.dataType || f.left.fieldName !== f.right.fieldName;
    }
    return false;
  });
}
