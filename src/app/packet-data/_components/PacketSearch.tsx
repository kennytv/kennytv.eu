'use client';

import { Search, X } from 'lucide-react';

interface PacketSearchProps {
  query: string;
  onChange: (query: string) => void;
  resultCount: number;
  totalCount: number;
}

export default function PacketSearch({
  query,
  onChange,
  resultCount,
  totalCount,
}: PacketSearchProps) {
  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-dim" />
      <input
        type="text"
        value={query}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Search packets, fields, or types..."
        className="w-full rounded-lg border border-border bg-bg-surface py-2.5 pl-10 pr-20
          text-sm text-text placeholder:text-text-dim
          focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary
          transition-colors"
      />
      {query && (
        <button
          type="button"
          onClick={() => onChange('')}
          className="absolute right-16 top-1/2 -translate-y-1/2 rounded p-0.5
            text-text-dim hover:text-text transition-colors"
          aria-label="Clear search"
        >
          <X className="h-4 w-4" />
        </button>
      )}
      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-text-dim">
        {resultCount}/{totalCount}
      </span>
    </div>
  );
}
