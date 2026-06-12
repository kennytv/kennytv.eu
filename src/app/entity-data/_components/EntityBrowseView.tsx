'use client';

import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { VersionEntities } from '../_lib/entityDataUtils';
import { filterEntities, getInheritedFieldGroups } from '../_lib/entityDataUtils';
import { getUrlParams, updateUrlParam, setUrlHash } from '../_lib/useUrlState';
import VersionSelector from './VersionSelector';
import EntitySearch from './EntitySearch';
import EntityNavigator from './EntityNavigator';
import EntityTable from './EntityTable';
import BackToTop from './BackToTop';

interface EntityBrowseViewProps {
  versions: string[];
  version: string;
  entities: VersionEntities | null;
  loading: boolean;
  onVersionChange: (v: string) => void;
  onCompare: () => void;
}

/** Briefly outline a card so the user can spot where a jump landed */
function flashElement(el: HTMLElement) {
  el.classList.remove('flash-highlight');
  void el.offsetWidth; // restart the animation if it is already running
  el.classList.add('flash-highlight');
  window.setTimeout(() => el.classList.remove('flash-highlight'), 1700);
}

export default function EntityBrowseView({ versions, version, entities, loading, onVersionChange, onCompare }: EntityBrowseViewProps) {
  const latestVersion = versions[versions.length - 1];

  const [query, setQuery] = useState(() => getUrlParams().get('q') ?? '');
  const [showInherited, setShowInherited] = useState(false);
  const hasScrolledToHash = useRef(false);

  // Persist the search query in the URL (debounced to keep replaceState calls low)
  useEffect(() => {
    const timeout = window.setTimeout(() => updateUrlParam('q', query.trim() ? query : null), 300);
    return () => window.clearTimeout(timeout);
  }, [query]);

  const setVersion = useCallback((v: string) => {
    onVersionChange(v);
    updateUrlParam('version', v === latestVersion ? null : v);
  }, [latestVersion, onVersionChange]);

  const filteredNames = useMemo(
    () => entities ? filterEntities(entities, query) : [],
    [entities, query],
  );
  const visibleSet = useMemo(() => new Set(filteredNames), [filteredNames]);

  const scrollToEntity = useCallback((name: string) => {
    const anchor = name.toLowerCase().replace(/\s+/g, '-');
    const el = document.getElementById(anchor);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      flashElement(el);
      setUrlHash(anchor);
    }
  }, []);

  // Scroll to hash anchor on initial load
  useEffect(() => {
    if (hasScrolledToHash.current || filteredNames.length === 0) return;
    hasScrolledToHash.current = true;
    const hash = window.location.hash.slice(1);
    if (!hash) return;
    // Wait for DOM to render the entity tables
    requestAnimationFrame(() => {
      const el = document.getElementById(hash);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        flashElement(el);
      }
    });
  }, [filteredNames]);

  const handleSuperClassClick = useCallback((name: string) => {
    setQuery('');
    // Wait for re-render so the target entity is in the DOM
    requestAnimationFrame(() => scrollToEntity(name));
  }, [scrollToEntity, setQuery]);

  const jumpToFirstResult = useCallback(() => {
    if (filteredNames.length > 0) scrollToEntity(filteredNames[0]);
  }, [filteredNames, scrollToEntity]);

  return (
    <>
      {/* Controls (sticky on desktop so search and version stay reachable) */}
      <div className="mb-6 md:sticky md:top-2 md:z-20">
        <div className="card p-4">
          <div className="flex flex-wrap items-center gap-3">
            <VersionSelector versions={versions} selected={version} onChange={setVersion} label="Version:" withArrows />
            <button
              type="button"
              onClick={onCompare}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white
                shadow-md shadow-primary/25 hover:bg-primary-dark transition-colors"
            >
              Compare versions
            </button>
            <div className="min-w-[220px] flex-1">
              <EntitySearch
                query={query}
                onChange={setQuery}
                onSubmit={jumpToFirstResult}
                resultCount={filteredNames.length}
                totalCount={entities ? Object.keys(entities).length : 0}
              />
            </div>
          </div>

          <label className="mt-3 flex items-center gap-2 text-sm text-text-muted cursor-pointer select-none w-fit">
            <input
              type="checkbox"
              checked={showInherited}
              onChange={(e) => setShowInherited(e.target.checked)}
              className="accent-primary h-4 w-4 cursor-pointer"
            />
            Show inherited fields
          </label>
        </div>
      </div>

      {/* Initial loading spinner (no data yet) */}
      {loading && !entities && (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-3 border-primary border-t-transparent" />
        </div>
      )}

      {/* Content stays visible (dimmed) while the next version loads */}
      {entities && (
        <>
          <div className={`transition-opacity duration-200 ${loading ? 'pointer-events-none opacity-40' : ''}`}>
            {/* Hierarchy navigator */}
            <div className="mb-6">
              <EntityNavigator
                entities={entities}
                query={query}
                visibleEntities={visibleSet}
                onEntityClick={scrollToEntity}
                showInherited={showInherited}
              />
            </div>

            {/* Entity tables */}
            {filteredNames.length === 0 ? (
              <div className="card p-8 text-center text-text-dim">
                No entities match your search.
              </div>
            ) : (
              <div className="space-y-4">
                {filteredNames.map((name) => (
                  <EntityTable
                    key={name}
                    name={name}
                    entity={entities[name]}
                    inheritedGroups={showInherited ? getInheritedFieldGroups(entities, name) : undefined}
                    onSuperClassClick={handleSuperClassClick}
                  />
                ))}
              </div>
            )}
          </div>

          <BackToTop />
        </>
      )}
    </>
  );
}
