'use client'

import { useEffect, useRef, useState } from 'react'

import { cn } from '@/components/ui'

export interface MenuItem {
  label: string
  description?: string
  onSelect: () => void
}

const VARIANTS = {
  primary: 'bg-accent-solid text-accent-fg hover:bg-accent-solid-hover',
  secondary: 'border border-border bg-surface text-ink hover:bg-surface-elevated',
  ghost: 'text-ink-secondary hover:bg-surface-elevated hover:text-ink',
} as const

export function ActionMenu({
  label,
  items,
  variant = 'primary',
  size = 'md',
  ariaLabel,
}: {
  label: string
  items: MenuItem[]
  variant?: keyof typeof VARIANTS
  size?: 'sm' | 'md'
  ariaLabel?: string
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label={ariaLabel ?? label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className={cn(
          'inline-flex select-none items-center justify-center gap-1.5 rounded-chip font-medium transition-colors duration-fast ease-standard',
          VARIANTS[variant],
          size === 'sm' ? 'h-8 px-3 text-sm' : 'h-10 px-4 text-base',
        )}
      >
        {label}
        <svg viewBox="0 0 24 24" className="size-3.5" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open ? (
        <div
          role="menu"
          className="absolute right-0 z-40 mt-1 min-w-48 overflow-hidden rounded-chip border border-border bg-surface p-1 shadow-modal"
        >
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false)
                item.onSelect()
              }}
              className="block w-full rounded-[6px] px-3 py-2 text-left transition-colors duration-fast hover:bg-surface-elevated"
            >
              <span className="block text-sm font-medium text-ink">{item.label}</span>
              {item.description ? (
                <span className="block text-xs text-ink-muted">{item.description}</span>
              ) : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}
