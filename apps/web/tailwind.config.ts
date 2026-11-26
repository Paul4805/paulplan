import type { Config } from 'tailwindcss'

import { tailwindFontSize } from './lib/tokens.generated'

/**
 * Colours reference CSS variables so light/dark switch without rebuilding.
 * Numeric scales that must be identical on Android come from the generated
 * token module, not from hand-copied values.
 */
const config: Config = {
  darkMode: 'class',
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}', './lib/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: 'var(--color-canvas)',
        surface: {
          DEFAULT: 'var(--color-surface)',
          elevated: 'var(--color-surface-elevated)',
        },
        border: {
          DEFAULT: 'var(--color-border)',
          strong: 'var(--color-border-strong)',
        },
        ink: {
          DEFAULT: 'var(--color-text-primary)',
          secondary: 'var(--color-text-secondary)',
          muted: 'var(--color-text-muted)',
        },
        accent: {
          DEFAULT: 'var(--color-accent)',
          text: 'var(--color-accent-text)',
          solid: 'var(--color-accent-solid)',
          'solid-hover': 'var(--color-accent-solid-hover)',
          subtle: 'var(--color-accent-subtle)',
          fg: 'var(--color-accent-fg)',
        },
        success: 'var(--color-success)',
        warning: 'var(--color-warning)',
        danger: 'var(--color-danger)',
        tag: {
          indigo: 'var(--tag-indigo)',
          sky: 'var(--tag-sky)',
          violet: 'var(--tag-violet)',
          amber: 'var(--tag-amber)',
          rose: 'var(--tag-rose)',
          emerald: 'var(--tag-emerald)',
        },
      },
      fontFamily: {
        sans: ['var(--font-inter)', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      fontSize: tailwindFontSize,
      borderRadius: {
        chip: 'var(--radius-chip)',
        card: 'var(--radius-card)',
        sheet: 'var(--radius-sheet)',
        pill: 'var(--radius-pill)',
      },
      transitionTimingFunction: {
        standard: 'var(--ease-standard)',
      },
      transitionDuration: {
        fast: 'var(--duration-fast)',
        base: 'var(--duration-base)',
      },
      boxShadow: {
        popover: 'var(--shadow-popover)',
        modal: 'var(--shadow-modal)',
      },
    },
  },
  plugins: [],
}

export default config
