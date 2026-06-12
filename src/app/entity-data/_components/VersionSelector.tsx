'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';

interface VersionSelectorProps {
  versions: string[];
  selected: string;
  onChange: (version: string) => void;
  label: string;
  withArrows?: boolean;
}

const ARROW_CLASS = `rounded-lg border border-border bg-bg-surface p-2 text-text-muted
  hover:border-border-light hover:text-text transition-colors
  disabled:pointer-events-none disabled:opacity-40`;

export default function VersionSelector({
  versions,
  selected,
  onChange,
  label,
  withArrows = false,
}: VersionSelectorProps) {
  const index = versions.indexOf(selected);

  return (
    <label className="flex items-center gap-2 text-sm text-text-muted">
      <span className="font-medium">{label}</span>
      {withArrows && (
        <button
          type="button"
          onClick={() => onChange(versions[index - 1])}
          disabled={index <= 0}
          aria-label="Previous version"
          className={ARROW_CLASS}
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
      )}
      <select
        value={selected}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-lg border border-border bg-bg-surface px-3 py-2 text-sm text-text
          focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary
          transition-colors cursor-pointer"
      >
        {versions.map((v) => (
          <option key={v} value={v}>
            {v}
          </option>
        ))}
      </select>
      {withArrows && (
        <button
          type="button"
          onClick={() => onChange(versions[index + 1])}
          disabled={index < 0 || index >= versions.length - 1}
          aria-label="Next version"
          className={ARROW_CLASS}
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      )}
    </label>
  );
}
