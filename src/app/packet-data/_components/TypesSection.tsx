'use client';

import type { CustomType } from '../_lib/packetDataUtils';

interface TypesSectionProps {
  types: Record<string, CustomType>;
}

export default function TypesSection({ types }: TypesSectionProps) {
  const typeNames = Object.keys(types).sort();

  if (typeNames.length === 0) return null;

  return (
    <div className="mt-8">
      <h2 className="mb-4 text-lg font-semibold text-text">Base Types</h2>
      <div className="space-y-3">
        {typeNames.map((name) => {
          const type = types[name];
          const hasLimits = type.fields.some((f) => f.limit != null);

          return (
            <div key={name} id={name.toLowerCase().replace(/[.\s]+/g, '-')} className="card p-4 md:p-6">
              <h4 className="mb-3 text-base font-semibold text-text">{name}</h4>
              {type.fields.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border text-left text-text-dim">
                        <th className="py-2 pr-4 font-medium">Field Name</th>
                        <th className="py-2 pr-4 font-medium">Type</th>
                        {hasLimits && <th className="py-2 font-medium">Limit</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {type.fields.map((field, i) => (
                        <tr key={`${field.name}-${i}`} className="border-b border-border/50">
                          <td className="py-1.5 pr-4 font-mono text-text">{field.name}</td>
                          <td className="py-1.5 pr-4 font-mono text-amber">{field.type}</td>
                          {hasLimits && (
                            <td className="py-1.5 font-mono text-text-muted">
                              {field.limit ?? ''}
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-sm text-text-dim">Polymorphic type (dispatch codec).</p>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
