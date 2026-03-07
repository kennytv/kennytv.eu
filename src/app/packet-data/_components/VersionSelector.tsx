'use client';

interface VersionSelectorProps {
  versions: string[];
  selected: string;
  onChange: (version: string) => void;
  label: string;
}

export default function VersionSelector({
  versions,
  selected,
  onChange,
  label,
}: VersionSelectorProps) {
  return (
    <label className="flex items-center gap-2 text-sm text-text-muted">
      <span className="font-medium">{label}</span>
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
    </label>
  );
}
