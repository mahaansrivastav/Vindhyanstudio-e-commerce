// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import type { ReactNode } from 'react'

/** Sticky action bar for phones (price + main button), clear of the iPhone home indicator. Hidden from sm up. */
export function MobileBar({ children }: { children: ReactNode }) {
  return (
    <>
      <div className="h-20 sm:hidden" aria-hidden />
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-white/95 px-4 pt-3 shadow-[0_-6px_20px_rgba(0,0,0,0.06)] backdrop-blur-md [padding-bottom:max(0.75rem,env(safe-area-inset-bottom))] sm:hidden">
        <div className="flex items-center gap-3">{children}</div>
      </div>
    </>
  )
}
