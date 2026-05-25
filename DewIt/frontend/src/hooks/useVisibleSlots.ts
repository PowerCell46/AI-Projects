import { useState, useEffect } from 'react'

/**
 * Returns the number of carousel slots visible at the current viewport width.
 * Mirrors the breakpoints defined in Carousel.css so the JS logic stays in sync
 * with what CSS actually renders.
 */

const SMALL_BREAKPOINTS: { maxWidth: number; slots: number }[] = [
  { maxWidth: 480, slots: 1 },
  { maxWidth: 640, slots: 2 },
  { maxWidth: 860, slots: 3 },
  { maxWidth: 1100, slots: 4 },
]

function getSlots(width: number): number {
  for (const bp of SMALL_BREAKPOINTS) {
    if (width <= bp.maxWidth) return bp.slots
  }
  return 5
}

export function useVisibleSlots(): number {
  const [slots, setSlots] = useState(() =>
    typeof window === 'undefined' ? 5 : getSlots(window.innerWidth),
  )

  useEffect(() => {
    const handler = () => setSlots(getSlots(window.innerWidth))
    window.addEventListener('resize', handler)
    return () => window.removeEventListener('resize', handler)
  }, [])

  return slots
}
