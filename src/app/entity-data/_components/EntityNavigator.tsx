'use client';

import { useState, useMemo, useCallback, useEffect } from 'react';
import { ChevronRight, Home } from 'lucide-react';
import type { VersionEntities } from '../_lib/entityDataUtils';
import { getAncestorChain, getDirectChildren, hasChildren, getTotalFieldCount } from '../_lib/entityDataUtils';
import NavigatorSearchResults from './NavigatorSearchResults';

interface EntityNavigatorProps {
  entities: VersionEntities;
  query: string;
  visibleEntities: Set<string>;
  onEntityClick: (name: string) => void;
  showInherited: boolean;
}

export default function EntityNavigator({
  entities,
  query,
  visibleEntities,
  onEntityClick,
  showInherited,
}: EntityNavigatorProps) {
  // Path represents the breadcrumb trail; empty = root level
  const [path, setPath] = useState<string[]>([]);

  // Reset path when entities change (version switch)
  useEffect(() => setPath([]), [entities]);

  const currentName = path.length > 0 ? path[path.length - 1] : null;

  const children = useMemo(() => {
    if (!currentName) {
      // Root level: entities whose superClass is not in the dataset
      return Object.entries(entities)
        .filter(([, info]) => !info.superClass || !entities[info.superClass])
        .map(([name]) => name)
        .sort();
    }
    return getDirectChildren(entities, currentName);
  }, [entities, currentName]);

  const navigateTo = useCallback((name: string) => {
    const chain = getAncestorChain(entities, name);
    // Chain includes the entity itself; if it has children, navigate into it.
    // If it's a leaf, navigate to its parent and scroll to it.
    if (hasChildren(entities, name)) {
      setPath(chain);
    } else {
      setPath(chain.slice(0, -1));
      onEntityClick(name);
    }
  }, [entities, onEntityClick]);

  const handleChipClick = useCallback((name: string) => {
    if (hasChildren(entities, name)) {
      setPath((prev) => [...prev, name]);
    } else {
      onEntityClick(name);
    }
  }, [entities, onEntityClick]);

  const handleBreadcrumbClick = useCallback((index: number) => {
    setPath((prev) => prev.slice(0, index + 1));
  }, []);

  const getFieldCount = useCallback((name: string) => {
    if (showInherited) return getTotalFieldCount(entities, name);
    return entities[name]?.fields.length ?? 0;
  }, [entities, showInherited]);

  const isSearching = query.trim().length > 0;

  return (
    <div className="card p-4 md:p-6">
      <h2 className="mb-3 text-lg font-semibold text-text">
        {isSearching ? 'Search Results' : 'Entity Hierarchy'}
      </h2>

      {isSearching ? (
        <NavigatorSearchResults
          entities={entities}
          visibleEntities={visibleEntities}
          onNavigate={navigateTo}
          onEntityClick={onEntityClick}
          showInherited={showInherited}
        />
      ) : (
        <>
          {/* Breadcrumb */}
          <div className="mb-4 flex flex-wrap items-center gap-1 text-sm">
            <button
              type="button"
              onClick={() => setPath([])}
              className={`rounded px-1.5 py-0.5 transition-colors ${
                path.length === 0
                  ? 'text-text font-medium'
                  : 'text-primary hover:text-primary-light'
              }`}
            >
              <Home className="h-4 w-4" />
            </button>
            {path.map((segment, i) => (
              <span key={segment} className="flex items-center gap-1">
                <ChevronRight className="h-3.5 w-3.5 text-text-dim" />
                <button
                  type="button"
                  onClick={() => handleBreadcrumbClick(i)}
                  className={`rounded px-1.5 py-0.5 transition-colors ${
                    i === path.length - 1
                      ? 'text-text font-medium'
                      : 'text-primary hover:text-primary-light'
                  }`}
                >
                  {segment}
                </button>
              </span>
            ))}
          </div>

          {/* Children grid */}
          {children.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {children.map((name) => {
                const isParent = hasChildren(entities, name);
                const count = getFieldCount(name);
                return (
                  <button
                    key={name}
                    type="button"
                    onClick={() => handleChipClick(name)}
                    className={`group flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm transition-all ${
                      isParent
                        ? 'border-primary/30 bg-primary/5 text-text hover:border-primary hover:bg-primary/10'
                        : 'border-border bg-bg-surface text-text-muted hover:border-border-light hover:text-text'
                    }`}
                  >
                    {name}
                    {count > 0 && (
                      <span className="text-text-dim text-xs">({count})</span>
                    )}
                    {isParent && (
                      <ChevronRight className="h-3.5 w-3.5 text-text-dim group-hover:text-primary transition-colors" />
                    )}
                  </button>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-text-dim">No children.</p>
          )}
        </>
      )}
    </div>
  );
}
