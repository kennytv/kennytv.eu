'use client';

import { useState, useEffect, useCallback } from 'react';
import type { EntityDataJson } from '../_lib/entityDataUtils';
import { getUrlParams, setUrlParams } from '../_lib/useUrlState';
import EntityBrowseView from './EntityBrowseView';
import EntityDiffView from './EntityDiffView';

export default function EntityDataClient() {
  const [data, setData] = useState<EntityDataJson | null>(null);
  const [error, setError] = useState(false);
  const [diffMode, setDiffMode] = useState(() => getUrlParams().has('diff'));

  useEffect(() => {
    fetch('/entity-data.json')
      .then((res) => res.json())
      .then((json: EntityDataJson) => setData(json))
      .catch(() => setError(true));
  }, []);

  const enterDiffMode = useCallback(() => {
    setDiffMode(true);
    const params = getUrlParams();
    // Clear browse-mode params, add diff flag
    params.delete('version');
    params.set('diff', '');
    setUrlParams(params);
    // Clear hash
    window.history.replaceState(null, '', `${window.location.pathname}?${params.toString()}`);
  }, []);

  const exitDiffMode = useCallback(() => {
    setDiffMode(false);
    const params = getUrlParams();
    // Clear diff-mode params
    params.delete('diff');
    params.delete('from');
    params.delete('to');
    setUrlParams(params);
  }, []);

  if (error) {
    return (
      <div className="flex items-center justify-center py-20 text-center text-text-dim">
        <div>
          <p className="mb-2 text-lg">Failed to load data</p>
          <p className="text-sm">entity-data.json not found or invalid</p>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (diffMode) {
    return <EntityDiffView data={data} onBack={exitDiffMode} />;
  }

  return <EntityBrowseView data={data} onCompare={enterDiffMode} />;
}
