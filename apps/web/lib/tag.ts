import type { CSSProperties } from 'react'

import type { TagColor } from './types'

export function tagTint(color: TagColor): CSSProperties {
  return {
    backgroundColor: `color-mix(in srgb, var(--tag-${color}) 12%, transparent)`,
    borderColor: `color-mix(in srgb, var(--tag-${color}) 32%, transparent)`,
  }
}

export function tagBar(color: TagColor): CSSProperties {
  return { backgroundColor: `var(--tag-${color})` }
}

export function tagDot(color: TagColor): CSSProperties {
  return { backgroundColor: `var(--tag-${color})` }
}
