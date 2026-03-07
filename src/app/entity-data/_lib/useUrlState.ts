'use client';

import { useCallback, useRef } from 'react';

/**
 * Read the current URL search params (client-side only).
 */
export function getUrlParams(): URLSearchParams {
  if (typeof window === 'undefined') return new URLSearchParams();
  return new URLSearchParams(window.location.search);
}

/**
 * Replace URL search params without triggering navigation or adding history entries.
 * Preserves the current hash fragment.
 */
export function setUrlParams(params: URLSearchParams): void {
  if (typeof window === 'undefined') return;
  const str = params.toString();
  const url = str
    ? `${window.location.pathname}?${str}${window.location.hash}`
    : `${window.location.pathname}${window.location.hash}`;
  window.history.replaceState(null, '', url);
}

/**
 * Update a single URL param. If value is empty/null, removes the param.
 */
export function updateUrlParam(key: string, value: string | null): void {
  const params = getUrlParams();
  if (value) {
    params.set(key, value);
  } else {
    params.delete(key);
  }
  setUrlParams(params);
}

/**
 * Set the URL hash without scrolling. Uses replaceState to avoid history entries.
 */
export function setUrlHash(hash: string): void {
  if (typeof window === 'undefined') return;
  const params = window.location.search;
  const newHash = hash ? `#${hash}` : '';
  window.history.replaceState(null, '', `${window.location.pathname}${params}${newHash}`);
}

/**
 * Hook that returns a stable updater for URL params.
 * Batches multiple param updates within the same frame into a single replaceState call.
 */
export function useUrlUpdater(): (updates: Record<string, string | null>) => void {
  const pending = useRef<Record<string, string | null>>({});
  const scheduled = useRef(false);

  return useCallback((updates: Record<string, string | null>) => {
    Object.assign(pending.current, updates);
    if (!scheduled.current) {
      scheduled.current = true;
      requestAnimationFrame(() => {
        const params = getUrlParams();
        for (const [key, value] of Object.entries(pending.current)) {
          if (value) {
            params.set(key, value);
          } else {
            params.delete(key);
          }
        }
        setUrlParams(params);
        pending.current = {};
        scheduled.current = false;
      });
    }
  }, []);
}
