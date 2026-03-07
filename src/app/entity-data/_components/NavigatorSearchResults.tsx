'use client';

import { useMemo } from 'react';
import { ChevronRight } from 'lucide-react';
import type { VersionEntities } from '../_lib/entityDataUtils';
import { getAncestorChain, hasChildren, getTotalFieldCount } from '../_lib/entityDataUtils';

interface NavigatorSearchResultsProps {
  entities: VersionEntities;
  visibleEntities: Set<string>;
  onNavigate: (name: string) => void;
  onEntityClick: (name: string) => void;
  showInherited: boolean;
}

export default function NavigatorSearchResults({
  entities,
  visibleEntities,
  onNavigate,
  onEntityClick,
  showInherited,
}: NavigatorSearchResultsProps) {
  const results = useMemo(() => {
    return Array.from(visibleEntities)
      .sort()
      .map((name) => ({
        name,
        ancestors: getAncestorChain(entities, name).slice(0, -1), // exclude self
        isParent: hasChildren(entities, name),
        fieldCount: showInherited
          ? getTotalFieldCount(entities, name)
          : (entities[name]?.fields.length ?? 0),
      }));
  }, [entities, visibleEntities, showInherited]);

  if (results.length === 0) {
    return <p className="text-sm text-text-dim">No matching entities.</p>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {results.map(({ name, ancestors, isParent, fieldCount }) => (
        <button
          key={name}
          type="button"
          onClick={() => (isParent ? onNavigate(name) : onEntityClick(name))}
          className="group flex flex-col items-start rounded-lg border border-border bg-bg-surface
            px-3 py-2 text-left transition-all hover:border-border-light"
        >
          <span className="flex items-center gap-1.5 text-sm font-medium text-text">
            {name}
            {fieldCount > 0 && (
              <span className="text-text-dim text-xs font-normal">({fieldCount})</span>
            )}
            {isParent && (
              <ChevronRight className="h-3.5 w-3.5 text-text-dim group-hover:text-primary transition-colors" />
            )}
          </span>
          {ancestors.length > 0 && (
            <span className="mt-0.5 text-xs text-text-dim">
              {ancestors.join(' > ')}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
