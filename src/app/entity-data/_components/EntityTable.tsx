'use client';

import { Hash } from 'lucide-react';
import type { EntityInfo, InheritedFieldGroup } from '../_lib/entityDataUtils';
import { setUrlHash } from '../_lib/useUrlState';

interface EntityTableProps {
  name: string;
  entity: EntityInfo;
  inheritedGroups?: InheritedFieldGroup[];
  onSuperClassClick?: (name: string) => void;
}

export default function EntityTable({ name, entity, inheritedGroups, onSuperClassClick }: EntityTableProps) {
  const hasInherited = inheritedGroups && inheritedGroups.some((g) => g.fields.length > 0);
  const anchor = toAnchor(name);

  return (
    <div id={anchor} className="card p-4 md:p-6">
      <h3 className="group mb-1 flex items-center gap-1.5 text-base font-semibold text-text">
        {name}
        <a
          href={`#${anchor}`}
          onClick={() => setUrlHash(anchor)}
          className="text-text-dim/0 transition-colors group-hover:text-text-dim hover:!text-primary"
          aria-label={`Link to ${name}`}
        >
          <Hash className="h-4 w-4" />
        </a>
      </h3>
      <p className="mb-3 text-xs text-text-dim">
        Extends:{' '}
        {entity.superClass ? (
          <button
            type="button"
            onClick={() => onSuperClassClick?.(entity.superClass!)}
            className="text-primary hover:text-primary-light transition-colors"
          >
            {entity.superClass}
          </button>
        ) : (
          <span className="text-text-muted">None</span>
        )}
      </p>

      {hasInherited || entity.fields.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-text-dim">
                <th className="py-2 pr-4 font-medium">Index</th>
                <th className="py-2 pr-4 font-medium">Data Type</th>
                <th className="py-2 pr-4 font-medium">Field Name</th>
                <th className="py-2 font-medium">Default</th>
              </tr>
            </thead>
            <tbody>
              {hasInherited &&
                inheritedGroups!.map((group) =>
                  group.fields.map((field, i) => (
                    <tr key={`${group.entityName}-${field.fieldName}`} className="border-b border-border/50">
                      <td className="py-1.5 pr-4 font-mono text-text-dim/60">{field.index}</td>
                      <td className="py-1.5 pr-4 font-mono text-amber/50">{field.dataType}</td>
                      <td className="py-1.5 pr-4 font-mono text-text-dim">
                        {field.fieldName}
                        {i === 0 && (
                          <button
                            type="button"
                            onClick={() => onSuperClassClick?.(group.entityName)}
                            className="ml-2 text-[11px] text-primary/60 hover:text-primary transition-colors"
                          >
                            {group.entityName}
                          </button>
                        )}
                      </td>
                      <td className="py-1.5 font-mono text-text-dim/60">{field.defaultValue ?? ''}</td>
                    </tr>
                  )),
                )}
              {entity.fields.map((field) => (
                <tr key={field.fieldName} className="border-b border-border/50">
                  <td className="py-1.5 pr-4 font-mono text-text-muted">{field.index}</td>
                  <td className="py-1.5 pr-4 font-mono text-amber">{field.dataType}</td>
                  <td className="py-1.5 pr-4 font-mono text-text">{field.fieldName}</td>
                  <td className="py-1.5 font-mono text-text-muted">{field.defaultValue ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-sm text-text-dim">No entity data fields.</p>
      )}
    </div>
  );
}

function toAnchor(name: string): string {
  return name.toLowerCase().replace(/\s+/g, '-');
}
