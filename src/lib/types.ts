export interface Project {
  title: string;
  description: string;
  href: string;
  icon: string;
}

export interface SocialLinkData {
  href: string;
  label: string;
  icon: string;
}

/* ── Secretgraph Types ────────────────────────────── */

export interface PlatformInfo {
  id: string;
  name: string;
  color: string;
}

export interface DataEntry {
  date: string;
  data: Record<string, Record<string, number>>;
}

export interface ServerData {
  versions: string[];
  info: PlatformInfo[];
  data: DataEntry[];
}

export interface ChartDataPoint {
  date: string;
  [key: string]: string | number;
}
