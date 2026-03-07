'use client';

interface TooltipEntry {
  dataKey?: string | number;
  name?: string;
  value?: number;
  color?: string;
}

interface ChartTooltipProps {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string;
}

export default function ChartTooltip({ active, payload, label }: ChartTooltipProps) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-lg border border-border bg-bg-card px-4 py-3 shadow-xl">
      <p className="mb-2 text-sm font-semibold text-text">{label}</p>
      {payload.map((entry) => (
        <div key={String(entry.dataKey)} className="flex items-center gap-2 text-sm">
          <span
            className="inline-block h-2.5 w-2.5 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          <span className="text-text-muted">{entry.name}:</span>
          <span className="font-medium text-text">
            {entry.value?.toLocaleString() ?? '0'}
          </span>
        </div>
      ))}
    </div>
  );
}
