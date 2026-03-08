'use client';

import { useState, useMemo, useCallback } from 'react';
import type { VersionEntities, EntityDiff } from '../_lib/entityDataUtils';
import { computeDiff, filterDiffs } from '../_lib/entityDataUtils';
import { updateUrlParam } from '../_lib/useUrlState';
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
  const [query, setQuery] = useState('');
  const [hideIndexOnly, setHideIndexOnly] = useState(true);

  const setLeftVersion = useCallback((v: string) => {
    onLeftVersionChange(v);
    updateUrlParam('from', v);
  }, [onLeftVersionChange]);

  const setRightVersion = useCallback((v: string) => {
    onRightVersionChange(v);
    updateUrlParam('to', v);
  }, [onRightVersionChange]);

  const bothLoaded = leftEntities && rightEntities;
  const eitherLoading = leftLoading || rightLoading;

  const allDiffs = useMemo(
    () => bothLoaded ? computeDiff(leftEntities, rightEntities) : [],
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

  const changedCount = allDiffs.filter((d) => d.status !== 'unchanged').length;

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
          <EntitySearch
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
            {changedCount} {changedCount === 1 ? 'entity' : 'entities'} changed
          </span>
        </div>
      </div>

      {/* Loading spinner */}
      {eitherLoading && (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-3 border-primary border-t-transparent" />
        </div>
      )}

      {/* Diff cards */}
      {!eitherLoading && (
        <>
          {leftVersion === rightVersion ? (
            <div className="card p-8 text-center text-text-dim">
              Select two different versions to compare.
            </div>
          ) : !bothLoaded ? (
            <div className="card p-8 text-center text-text-dim">
              Loading version data...
            </div>
          ) : filteredDiffs.length === 0 ? (
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

          <BackToTop />
        </>
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
