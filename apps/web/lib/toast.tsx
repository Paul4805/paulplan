'use client'

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

type Tone = 'default' | 'danger' | 'success'

interface ToastInput {
  title: string
  description?: string
  tone?: Tone
}

interface ToastItem extends ToastInput {
  id: string
}

type PushToast = (toast: ToastInput) => void

const ToastContext = createContext<PushToast>(() => {})

export function useToast(): PushToast {
  return useContext(ToastContext)
}

function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : Math.random().toString(36).slice(2)
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const push = useCallback<PushToast>((toast) => {
    const id = newId()
    setItems((current) => [...current.slice(-3), { ...toast, id }])
    setTimeout(() => {
      setItems((current) => current.filter((item) => item.id !== id))
    }, toast.tone === 'danger' ? 7000 : 4500)
  }, [])

  const value = useMemo(() => push, [push])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 sm:bottom-6 sm:left-auto sm:right-6 sm:items-end sm:px-0"
      >
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            className={[
              'pointer-events-auto w-full max-w-sm rounded-card border bg-surface-elevated p-3 shadow-popover',
              item.tone === 'danger'
                ? 'border-danger'
                : item.tone === 'success'
                  ? 'border-success'
                  : 'border-border',
            ].join(' ')}
          >
            <p className="text-sm font-medium text-ink">{item.title}</p>
            {item.description ? (
              <p className="mt-0.5 text-sm text-ink-muted">{item.description}</p>
            ) : null}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
