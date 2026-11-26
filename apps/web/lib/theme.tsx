'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import type { ThemeMode } from './types'

const STORAGE_KEY = 'slot-theme'

interface ThemeContextValue {
  mode: ThemeMode
  setMode: (mode: ThemeMode) => void
}

const ThemeContext = createContext<ThemeContextValue>({
  mode: 'system',
  setMode: () => {},
})

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext)
}

function applyMode(mode: ThemeMode): void {
  const root = document.documentElement
  root.classList.remove('light', 'dark')
  // 'system' deliberately sets neither class: tokens.generated.css contains a
  // prefers-color-scheme block that takes over.
  if (mode === 'light') root.classList.add('light')
  if (mode === 'dark') root.classList.add('dark')
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>('system')

  useEffect(() => {
    let stored: ThemeMode = 'system'
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw === 'light' || raw === 'dark' || raw === 'system') stored = raw
    } catch {
      // Private mode or blocked storage: keep the system default.
    }
    setModeState(stored)
    applyMode(stored)
  }, [])

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next)
    applyMode(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Non-fatal: the class is applied for this session.
    }
  }, [])

  const value = useMemo(() => ({ mode, setMode }), [mode, setMode])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
