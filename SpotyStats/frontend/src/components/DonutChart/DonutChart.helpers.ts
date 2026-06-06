export interface DonutSegment {
  value: number
  color: string
}

interface SegmentPath {
  path: string
  color: string
}

const CENTER = 50
const OUTER_RADIUS = 40
const INNER_RADIUS = 24
const FULL_TURN = Math.PI * 2

const pointAt = (radius: number, angle: number): string => {
  const x = CENTER + radius * Math.cos(angle)
  const y = CENTER + radius * Math.sin(angle)

  return `${x.toFixed(3)} ${y.toFixed(3)}`
}

/**
 * Builds an annular-sector path (outer arc → inner arc, reversed) for each
 * segment, starting at 12 o'clock and going clockwise.
 */
export const buildSegmentPaths = (segments: DonutSegment[]): SegmentPath[] => {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)

  if (total === 0) {
    return []
  }

  let angle = -Math.PI / 2

  return segments.map((segment) => {
    const sweep = (segment.value / total) * FULL_TURN
    const start = angle
    const end = angle + sweep
    const largeArc = sweep > Math.PI ? 1 : 0

    angle = end

    const path = [
      `M ${pointAt(OUTER_RADIUS, start)}`,
      `A ${OUTER_RADIUS} ${OUTER_RADIUS} 0 ${largeArc} 1 ${pointAt(OUTER_RADIUS, end)}`,
      `L ${pointAt(INNER_RADIUS, end)}`,
      `A ${INNER_RADIUS} ${INNER_RADIUS} 0 ${largeArc} 0 ${pointAt(INNER_RADIUS, start)}`,
      'Z',
    ].join(' ')

    return { path, color: segment.color }
  })
}
