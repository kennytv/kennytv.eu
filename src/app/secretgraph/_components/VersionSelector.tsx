'use client';

interface VersionSelectorProps {
  versions: string[];
  selectedVersions: string[];
  sumMode: boolean;
  onToggleVersion: (version: string) => void;
  onSelectAll: () => void;
  onToggleSum: () => void;
}

export default function VersionSelector({
  versions,
  selectedVersions,
  sumMode,
  onToggleVersion,
  onSelectAll,
  onToggleSum,
}: VersionSelectorProps) {
  const allSelected = selectedVersions.length === versions.length;

  return (
    <div className="card p-4 md:p-6 mb-6">
      <h2 className="mb-4 text-lg font-semibold text-text">Minecraft Versions</h2>

      {/* Version toggles */}
      <div className="mb-6 flex flex-wrap gap-x-2 gap-y-3">
        {versions.map((version) => {
          const active = selectedVersions.includes(version);
          return (
            <button
              key={version}
              type="button"
              onClick={() => onToggleVersion(version)}
              className={`rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 ${
                active
                  ? 'bg-primary text-white shadow-md shadow-primary/25'
                  : 'bg-bg-surface text-text-muted border border-border hover:border-border-light hover:text-text'
              }`}
            >
              {version}
            </button>
          );
        })}
      </div>

      {/* Action buttons */}
      <div className="flex flex-wrap items-center justify-center gap-3 border-t border-border pt-4">
        <button
          type="button"
          onClick={onSelectAll}
          className="min-w-[120px] rounded-lg bg-amber px-5 py-2.5 font-medium text-white
            hover:bg-amber-dark transition-colors"
        >
          {allSelected ? 'Deselect all' : 'Select all'}
        </button>
        <label
          className="inline-flex cursor-pointer items-center gap-2 rounded-lg bg-bg-surface
            border border-border px-5 py-2.5 font-medium text-text transition-colors
            hover:border-border-light"
        >
          <input
            type="checkbox"
            checked={sumMode}
            onChange={onToggleSum}
            className="h-4 w-4 accent-primary"
          />
          <span>Sum to one set</span>
        </label>
      </div>
    </div>
  );
}
