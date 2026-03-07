'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import type { ServerData } from '@/lib/types';
import { processChartData, buildDatasetConfig } from '../_lib/dataUtils';
import ServerChart from './ServerChart';
import VersionSelector from './VersionSelector';
import InfoCard from './InfoCard';

export default function SecretgraphClient() {
  const [json, setJson] = useState<ServerData | null>(null);
  const [error, setError] = useState(false);
  const [selectedVersions, setSelectedVersions] = useState<string[]>([]);
  const [sumMode, setSumMode] = useState(true);

  // Fetch data on mount
  useEffect(() => {
    fetch('/servers.json')
      .then((res) => res.json())
      .then((data: ServerData) => {
        setJson(data);
        // Default: select only the latest version
        if (data.versions.length > 0) {
          setSelectedVersions([data.versions[data.versions.length - 1]]);
        }
      })
      .catch(() => setError(true));
  }, []);

  const handleToggleVersion = useCallback((version: string) => {
    setSelectedVersions((prev) =>
      prev.includes(version)
        ? prev.filter((v) => v !== version)
        : [...prev, version].sort(),
    );
  }, []);

  const handleSelectAll = useCallback(() => {
    if (!json) return;
    setSelectedVersions((prev) =>
      prev.length === json.versions.length ? [] : [...json.versions],
    );
  }, [json]);

  const handleToggleSum = useCallback(() => {
    setSumMode((prev) => !prev);
  }, []);

  // Process data for the chart
  const chartData = useMemo(() => {
    if (!json) return [];
    return processChartData(json, selectedVersions, sumMode);
  }, [json, selectedVersions, sumMode]);

  const datasets = useMemo(() => {
    if (!json) return [];
    return buildDatasetConfig(json.info, selectedVersions, sumMode);
  }, [json, selectedVersions, sumMode]);

  // Loading state
  if (error) {
    return (
      <div className="flex items-center justify-center py-20 text-center text-text-dim">
        <div>
          <p className="mb-2 text-lg">Failed to load data</p>
          <p className="text-sm">servers.json not found or invalid</p>
        </div>
      </div>
    );
  }

  if (!json) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <>
      {/* Chart card */}
      <div className="card mb-6 p-4 md:p-6">
        <h1 className="mb-4 text-xl font-bold text-text md:text-2xl">
          Server Software Usage
        </h1>
        <ServerChart data={chartData} datasets={datasets} />
      </div>

      {/* Version selector */}
      <VersionSelector
        versions={json.versions}
        selectedVersions={selectedVersions}
        sumMode={sumMode}
        onToggleVersion={handleToggleVersion}
        onSelectAll={handleSelectAll}
        onToggleSum={handleToggleSum}
      />

      {/* Info */}
      <InfoCard />
    </>
  );
}
