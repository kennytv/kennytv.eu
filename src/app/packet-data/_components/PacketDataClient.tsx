'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import type { PacketDataIndex, VersionPacketData } from '../_lib/packetDataUtils';
import PacketBrowseView from './PacketBrowseView';
import PacketDiffView from './PacketDiffView';

export default function PacketDataClient() {
  const [index, setIndex] = useState<PacketDataIndex | null>(null);
  const [error, setError] = useState(false);
  const [diffMode, setDiffMode] = useState(false);

  // Cache fetched version data
  const cache = useRef<Record<string, VersionPacketData>>({});

  useEffect(() => {
    fetch('/packet-data/index.json')
      .then((res) => res.json())
      .then((json: PacketDataIndex) => setIndex(json))
      .catch(() => setError(true));
  }, []);

  const fetchVersion = useCallback(async (version: string): Promise<VersionPacketData> => {
    if (cache.current[version]) return cache.current[version];
    const res = await fetch(`/packet-data/${version}.json`);
    const data: VersionPacketData = await res.json();
    cache.current[version] = data;
    return data;
  }, []);

  if (error) {
    return (
      <div className="flex items-center justify-center py-20 text-center text-text-dim">
        <div>
          <p className="mb-2 text-lg">Failed to load data</p>
          <p className="text-sm">packet-data/index.json not found or invalid</p>
        </div>
      </div>
    );
  }

  if (!index) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (diffMode) {
    return (
      <PacketDiffView
        versions={index.versions}
        fetchVersion={fetchVersion}
        onBack={() => setDiffMode(false)}
      />
    );
  }

  return (
    <PacketBrowseView
      versions={index.versions}
      fetchVersion={fetchVersion}
      onCompare={() => setDiffMode(true)}
    />
  );
}
