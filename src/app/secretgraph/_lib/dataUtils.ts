import type { ServerData, ChartDataPoint } from '@/lib/types';

const DAY_MS = 24 * 60 * 60 * 1000;

function daysDiff(date1: Date, date2: Date): number {
  return Math.round(Math.abs((date1.getTime() - date2.getTime()) / DAY_MS));
}

export function processChartData(
  json: ServerData,
  selectedVersions: string[],
  sumMode: boolean,
): ChartDataPoint[] {
  const points: ChartDataPoint[] = [];
  const platformIds = json.info.map((p) => p.id);
  let lastDate: Date | null = null;
  const lastValues: Record<string, number> = {};

  for (const entry of json.data) {
    const date = new Date(entry.date);

    // Fill gaps between data points
    if (lastDate !== null) {
      const gap = daysDiff(lastDate, date);
      if (gap > 1) {
        const paddingPoints = buildPadding(lastDate, gap - 1, platformIds, selectedVersions, sumMode, lastValues);
        points.push(...paddingPoints);
      }
    }

    // Build this day's data point
    const point = buildDataPoint(date, entry.data, platformIds, selectedVersions, sumMode);
    if (point === null) {
      continue;
    }

    // Track last known values for gap-filling
    for (const key of Object.keys(point)) {
      if (key !== 'date') {
        lastValues[key] = point[key] as number;
      }
    }

    points.push(point);
    lastDate = date;
  }

  return points;
}

function buildDataPoint(
  date: Date,
  data: Record<string, Record<string, number>>,
  platformIds: string[],
  selectedVersions: string[],
  sumMode: boolean,
): ChartDataPoint | null {
  const point: ChartDataPoint = { date: date.toLocaleDateString() };
  let hasData = false;

  for (const platformId of platformIds) {
    const versionData = data[platformId] ?? {};

    if (sumMode) {
      let sum = 0;
      for (const version of selectedVersions) {
        sum += versionData[version] ?? 0;
      }
      if (sum !== 0 || hasData) {
        hasData = true;
      }
      point[platformId] = sum;
    } else {
      for (const version of selectedVersions) {
        const value = versionData[version] ?? 0;
        if (value !== 0) hasData = true;
        point[`${platformId}_${version}`] = value;
      }
    }
  }

  return hasData ? point : null;
}

function buildPadding(
  lastDate: Date,
  count: number,
  platformIds: string[],
  selectedVersions: string[],
  sumMode: boolean,
  lastValues: Record<string, number>,
): ChartDataPoint[] {
  const points: ChartDataPoint[] = [];
  const fromDate = new Date(lastDate);

  for (let i = 0; i < count; i++) {
    fromDate.setDate(fromDate.getDate() + 1);
    const point: ChartDataPoint = { date: fromDate.toLocaleDateString() };

    for (const platformId of platformIds) {
      if (sumMode) {
        point[platformId] = lastValues[platformId] ?? 0;
      } else {
        for (const version of selectedVersions) {
          const key = `${platformId}_${version}`;
          point[key] = lastValues[key] ?? 0;
        }
      }
    }

    points.push(point);
  }

  return points;
}

export function buildDatasetConfig(
  info: ServerData['info'],
  selectedVersions: string[],
  sumMode: boolean,
): { key: string; name: string; color: string }[] {
  const CYCLE_COLORS = [
    '#ef4444', '#f59e0b', '#eab308', '#22c55e',
    '#3b82f6', '#a855f7', '#6b7280',
  ];

  const datasets: { key: string; name: string; color: string }[] = [];

  for (const platform of info) {
    if (sumMode) {
      datasets.push({
        key: platform.id,
        name: platform.name,
        color: platform.color,
      });
    } else {
      for (const version of selectedVersions) {
        datasets.push({
          key: `${platform.id}_${version}`,
          name: `${platform.name} ${version}`,
          color: CYCLE_COLORS[datasets.length % CYCLE_COLORS.length],
        });
      }
    }
  }

  return datasets;
}
