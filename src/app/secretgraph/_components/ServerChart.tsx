'use client';

import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  Brush,
  CartesianGrid,
} from 'recharts';
import type { ChartDataPoint } from '@/lib/types';
import ChartTooltip from './ChartTooltip';

interface DatasetConfig {
  key: string;
  name: string;
  color: string;
}

interface ServerChartProps {
  data: ChartDataPoint[];
  datasets: DatasetConfig[];
}

function formatYAxis(value: number): string {
  if (value >= 1000) {
    return `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)}k`;
  }
  return String(value);
}

export default function ServerChart({ data, datasets }: ServerChartProps) {
  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center py-20 text-text-dim">
        No data for the selected versions.
      </div>
    );
  }

  // Compute a reasonable tick interval so labels don't overlap
  const tickInterval = Math.max(1, Math.floor(data.length / 12));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={data} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
        <XAxis
          dataKey="date"
          tick={{ fill: 'var(--color-text-dim)', fontSize: 12 }}
          interval={tickInterval}
          tickLine={false}
          axisLine={{ stroke: 'var(--color-border)' }}
        />
        <YAxis
          tickFormatter={formatYAxis}
          tick={{ fill: 'var(--color-text-dim)', fontSize: 12 }}
          tickLine={false}
          axisLine={{ stroke: 'var(--color-border)' }}
          label={{
            value: 'Servers',
            angle: -90,
            position: 'insideLeft',
            fill: 'var(--color-text-dim)',
            fontSize: 12,
          }}
        />
        <Tooltip content={<ChartTooltip />} />
        <Legend
          wrapperStyle={{ paddingTop: 12, fontSize: 13 }}
          iconType="circle"
          iconSize={8}
        />

        {datasets.map((ds) => (
          <Line
            key={ds.key}
            type="monotone"
            dataKey={ds.key}
            name={ds.name}
            stroke={ds.color}
            dot={false}
            strokeWidth={2}
            activeDot={{ r: 4, strokeWidth: 0 }}
            animationDuration={800}
          />
        ))}

        {/* Date range brush selector at bottom */}
        <Brush
          dataKey="date"
          height={30}
          stroke="var(--color-primary)"
          fill="var(--color-bg-card)"
          travellerWidth={10}
          tickFormatter={() => ''}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
