'use client';

import { useState, useMemo, useCallback } from 'react';
import type { EntityDataJson, EntityDiff } from '../_lib/entityDataUtils';
import { computeDiff, filterDiffs } from '../_lib/entityDataUtils';
import { getUrlParams, updateUrlParam } from '../_lib/useUrlState';
import VersionSelector from './VersionSelector';
import EntitySearch from './EntitySearch';
import DiffCard from './DiffCard';
import BackToTop from './BackToTop';

interface EntityDiffViewProps {
  data: EntityDataJson;
  onBack: () => void;
}

export default function EntityDiffView({ data, onBack }: EntityDiffViewProps) {
  const versions = data.versions;

  // Initialize from URL params
  const [leftVersion, setLeftVersionState] = useState(() => {
    const param = getUrlParams().get('from');
    return param && versions.includes(param) ? param : (versions.length >= 2 ? versions[versions.length - 2] : versions[0]);
  });
  const [rightVersion, setRightVersionState] = useState(() => {
    const param = getUrlParams().get('to');
    return param && versions.includes(param) ? param : versions[versions.length - 1];
  });
  const [query, setQuery] = useState('');
  const [hideIndexOnly, setHideIndexOnly] = useState(true);

  const setLeftVersion = useCallback((v: string) => {
    setLeftVersionState(v);
    updateUrlParam('from', v);
  }, []);

  const setRightVersion = useCallback((v: string) => {
    setRightVersionState(v);
    updateUrlParam('to', v);
  }, []);

  const allDiffs = useMemo(
    () => computeDiff(data.data[leftVersion] ?? {}, data.data[rightVersion] ?? {}),
    [data, leftVersion, rightVersion],
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

      {/* Diff cards */}
      {leftVersion === rightVersion ? (
        <div className="card p-8 text-center text-text-dim">
          Select two different versions to compare.
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
