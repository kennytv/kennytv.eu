'use client';

import { useRef, useEffect, useCallback } from 'react';
import { Search, X } from 'lucide-react';

interface EntitySearchProps {
  query: string;
  onChange: (query: string) => void;
  resultCount: number;
  totalCount: number;
}

export default function EntitySearch({
  query,
  onChange,
  resultCount,
  totalCount,
}: EntitySearchProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    // '/' to focus search (when not already in an input/textarea)
    if (e.key === '/' && !isInputFocused()) {
      e.preventDefault();
      inputRef.current?.focus();
      return;
    }
    // Escape to clear and blur
    if (e.key === 'Escape' && document.activeElement === inputRef.current) {
      e.preventDefault();
      onChange('');
      inputRef.current?.blur();
    }
  }, [onChange]);

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-dim" />
      <input
        ref={inputRef}
        type="text"
        value={query}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Search entities or fields..."
        className="w-full rounded-lg border border-border bg-bg-surface py-2.5 pl-10 pr-24
          text-sm text-text placeholder:text-text-dim
          focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary
          transition-colors"
      />
      {query ? (
        <button
          type="button"
          onClick={() => onChange('')}
          className="absolute right-16 top-1/2 -translate-y-1/2 rounded p-0.5
            text-text-dim hover:text-text transition-colors"
          aria-label="Clear search"
        >
          <X className="h-4 w-4" />
        </button>
      ) : (
        <kbd className="pointer-events-none absolute right-16 top-1/2 -translate-y-1/2
          rounded border border-border bg-bg-surface px-1.5 py-0.5 text-[10px]
          font-mono text-text-dim leading-none">/</kbd>
      )}
      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-text-dim">
        {resultCount}/{totalCount}
      </span>
    </div>
  );
}

function isInputFocused(): boolean {
  const el = document.activeElement;
  if (!el) return false;
  const tag = el.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || (el as HTMLElement).isContentEditable;
}
