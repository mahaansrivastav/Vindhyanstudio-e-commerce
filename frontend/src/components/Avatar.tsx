// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.

/** Initials in a circle (or a person icon when there's no name). */
export function Avatar({ name, size = 'md' }: { name?: string | null; size?: 'sm' | 'md' }) {
  const initials = (name ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join('')
  return (
    <span className={`flex shrink-0 items-center justify-center rounded-full bg-brand font-medium text-white ${size === 'sm' ? 'h-7 w-7 text-[11px]' : 'h-11 w-11 text-sm'}`}>
      {initials || (
        <svg viewBox="0 0 24 24" className={size === 'sm' ? 'h-4 w-4' : 'h-5 w-5'} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>
      )}
    </span>
  )
}
