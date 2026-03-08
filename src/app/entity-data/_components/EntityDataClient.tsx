'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import type { VersionEntities } from '../_lib/entityDataUtils';
import { fetchVersions, fetchVersionData } from '../_lib/entityDataUtils';
import { getUrlParams, setUrlParams } from '../_lib/useUrlState';
import EntityBrowseView from './EntityBrowseView';
import EntityDiffView from './EntityDiffView';

export default function EntityDataClient() {
  const [versions, setVersions] = useState<string[] | null>(null);
  const [error, setError] = useState(false);
  const [diffMode, setDiffMode] = useState(() => getUrlParams().has('diff'));

  // Cache of loaded version data
  const cache = useRef<Record<string, VersionEntities>>({});

  // Browse mode state
  const [browseVersion, setBrowseVersionState] = useState<string | null>(null);
  const [browseEntities, setBrowseEntities] = useState<VersionEntities | null>(null);
  const [browseLoading, setBrowseLoading] = useState(false);

  // Diff mode state
  const [leftVersion, setLeftVersionState] = useState<string | null>(null);
  const [rightVersion, setRightVersionState] = useState<string | null>(null);
  const [leftEntities, setLeftEntities] = useState<VersionEntities | null>(null);
  const [rightEntities, setRightEntities] = useState<VersionEntities | null>(null);
  const [leftLoading, setLeftLoading] = useState(false);
  const [rightLoading, setRightLoading] = useState(false);

  // Load a version, returning cached data if available
  const loadVersion = useCallback(async (version: string): Promise<VersionEntities> => {
    if (cache.current[version]) return cache.current[version];
    const data = await fetchVersionData(version);
    cache.current[version] = data;
    return data;
  }, []);

  // Fetch version list on mount
  useEffect(() => {
    fetchVersions()
      .then((v) => setVersions(v))
      .catch(() => setError(true));
  }, []);

  // Once versions are loaded, initialize browse + diff selections and load initial data
  useEffect(() => {
    if (!versions || versions.length === 0) return;
    const params = getUrlParams();
    const latestVersion = versions[versions.length - 1];

    // Browse
    const browseParam = params.get('version');
    const initialBrowse = browseParam && versions.includes(browseParam) ? browseParam : latestVersion;
    setBrowseVersionState(initialBrowse);

    // Diff
    const fromParam = params.get('from');
    const toParam = params.get('to');
    const initialLeft = fromParam && versions.includes(fromParam) ? fromParam : (versions.length >= 2 ? versions[versions.length - 2] : versions[0]);
    const initialRight = toParam && versions.includes(toParam) ? toParam : latestVersion;
    setLeftVersionState(initialLeft);
    setRightVersionState(initialRight);
  }, [versions]);

  // Load browse version data when selection changes
  useEffect(() => {
    if (!browseVersion) return;
    let cancelled = false;
    setBrowseLoading(true);
    loadVersion(browseVersion)
      .then((data) => { if (!cancelled) setBrowseEntities(data); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setBrowseLoading(false); });
    return () => { cancelled = true; };
  }, [browseVersion, loadVersion]);

  // Load left diff version data
  useEffect(() => {
    if (!leftVersion) return;
    let cancelled = false;
    setLeftLoading(true);
    loadVersion(leftVersion)
      .then((data) => { if (!cancelled) setLeftEntities(data); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLeftLoading(false); });
    return () => { cancelled = true; };
  }, [leftVersion, loadVersion]);

  // Load right diff version data
  useEffect(() => {
    if (!rightVersion) return;
    let cancelled = false;
    setRightLoading(true);
    loadVersion(rightVersion)
      .then((data) => { if (!cancelled) setRightEntities(data); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setRightLoading(false); });
    return () => { cancelled = true; };
  }, [rightVersion, loadVersion]);

  // Version change handlers
  const setBrowseVersion = useCallback((v: string) => {
    setBrowseVersionState(v);
  }, []);

  const setLeftVersion = useCallback((v: string) => {
    setLeftVersionState(v);
  }, []);

  const setRightVersion = useCallback((v: string) => {
    setRightVersionState(v);
  }, []);

  const enterDiffMode = useCallback(() => {
    setDiffMode(true);
    const params = getUrlParams();
    params.delete('version');
    params.set('diff', '');
    setUrlParams(params);
    window.history.replaceState(null, '', `${window.location.pathname}?${params.toString()}`);
  }, []);

  const exitDiffMode = useCallback(() => {
    setDiffMode(false);
    const params = getUrlParams();
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
          <p className="text-sm">Could not load entity data files</p>
        </div>
      </div>
    );
  }

  if (!versions || !browseVersion || !leftVersion || !rightVersion) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (diffMode) {
    return (
      <EntityDiffView
        versions={versions}
        leftVersion={leftVersion}
        rightVersion={rightVersion}
        leftEntities={leftEntities}
        rightEntities={rightEntities}
        leftLoading={leftLoading}
        rightLoading={rightLoading}
        onLeftVersionChange={setLeftVersion}
        onRightVersionChange={setRightVersion}
        onBack={exitDiffMode}
      />
    );
  }

  return (
    <EntityBrowseView
      versions={versions}
      version={browseVersion}
      entities={browseEntities}
      loading={browseLoading}
      onVersionChange={setBrowseVersion}
      onCompare={enterDiffMode}
    />
  );
}
