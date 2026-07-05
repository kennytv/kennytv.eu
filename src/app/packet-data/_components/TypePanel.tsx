'use client';

import { useEffect, useRef } from 'react';
import type { PacketNode } from '../_lib/packetDataUtils';
import NodeTree from './NodeTree';

interface TypePanelProps {
  /** Navigation stack of type names; the last entry is displayed. */
  stack: string[];
  types: Record<string, PacketNode>;
  onOpen: (name: string) => void;
  onJumpTo: (index: number) => void;
  onClose: () => void;
}

/**
 * Side panel for browsing shared types without losing your place in the packet list.
 * Nested type links push onto a breadcrumb trail.
 */
export default function TypePanel({ stack, types, onOpen, onJumpTo, onClose }: TypePanelProps) {
  const name = stack[stack.length - 1];
  const node = types[name];
  const bodyRef = useRef<HTMLDivElement>(null);
  const trailRef = useRef<HTMLDivElement>(null);

  // Reset content scroll and keep the newest breadcrumb in view when the shown type changes; Esc closes.
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 });
    trailRef.current?.scrollTo({ left: trailRef.current.scrollWidth });
  }, [name, stack.length]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <>
      {/* Invisible backdrop — click anywhere outside the panel to close, without dimming */}
      <div className="fixed inset-0 z-40" onClick={onClose} aria-hidden="true" />
      <aside
        className="fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l border-border
          bg-bg-card shadow-2xl shadow-black/50 sm:w-[30rem]"
        aria-label="Type details"
      >
      {/* Header: breadcrumb trail + close */}
      <div className="flex items-center gap-2 border-b border-border p-3">
        {stack.length > 1 && (
          <button
            type="button"
            onClick={() => onJumpTo(stack.length - 2)}
            className="rounded-md border border-border bg-bg-surface px-2 py-1 text-sm text-text-muted
              hover:border-border-light hover:text-text transition-colors"
            aria-label="Back to previous type"
          >
            ←
          </button>
        )}
        <div
          ref={trailRef}
          className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto whitespace-nowrap text-sm
            [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {stack.map((entry, i) => (
            <span key={`${entry}-${i}`} className="flex shrink-0 items-center gap-1">
              {i > 0 && <span className="text-text-dim">›</span>}
              {i === stack.length - 1 ? (
                <span className="max-w-[16rem] truncate font-semibold text-text" title={entry}>
                  {entry}
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => onJumpTo(i)}
                  title={entry}
                  className="max-w-[11rem] truncate text-primary hover:text-primary-light transition-colors"
                >
                  {entry}
                </button>
              )}
            </span>
          ))}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md px-2 py-1 text-text-dim hover:text-text transition-colors"
          aria-label="Close type panel"
        >
          ✕
        </button>
      </div>

      {/* Body */}
      <div ref={bodyRef} className="min-h-0 flex-1 overflow-y-auto p-4">
        {node ? (
          <NodeTree node={node} onTypeClick={onOpen} />
        ) : (
          <p className="text-sm text-text-dim">Unknown type: {name}</p>
        )}
      </div>
      </aside>
    </>
  );
}
