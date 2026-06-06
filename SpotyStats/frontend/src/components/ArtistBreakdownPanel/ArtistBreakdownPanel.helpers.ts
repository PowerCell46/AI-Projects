import type { ArtistShare } from '../../services/listeningService'


export type BreakdownMode = 'Tracks' | 'Time'

export interface LegendEntry {
  name: string
  value: number
  displayValue: string
  color: string
}

const CHART_COLORS = [
  'var(--chart-1)',
  'var(--chart-2)',
  'var(--chart-3)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--chart-6)',
]

const TAIL_COLOR = 'var(--chart-6)'
const MAX_NAMED_ARTISTS = 5

export const formatListeningTime = (timeMs: number): string => {
  const totalMinutes = Math.round(timeMs / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`
}

const valueOf = (share: ArtistShare, mode: BreakdownMode): number =>
  mode === 'Tracks' ? share.trackCount : share.listeningTimeMs

const displayValueOf = (value: number, mode: BreakdownMode): string =>
  mode === 'Tracks' ? String(value) : formatListeningTime(value)

/**
 * Sorts artists by the selected metric descending, keeps the top five with
 * their own chart colors, and lumps everything past that into a slate
 * "Others" tail — per the design brief's donut spec.
 */
export const buildLegendEntries = (
  shares: ArtistShare[],
  mode: BreakdownMode,
): LegendEntry[] => {
  const sorted = [...shares].sort((a, b) => valueOf(b, mode) - valueOf(a, mode))
  const named = sorted.slice(0, MAX_NAMED_ARTISTS)
  const tail = sorted.slice(MAX_NAMED_ARTISTS)

  const entries: LegendEntry[] = named.map((share, index) => ({
    name: share.artistName,
    value: valueOf(share, mode),
    displayValue: displayValueOf(valueOf(share, mode), mode),
    color: CHART_COLORS[index],
  }))

  if (tail.length > 0) {
    const tailValue = tail.reduce((sum, share) => sum + valueOf(share, mode), 0)

    entries.push({
      name: 'Others',
      value: tailValue,
      displayValue: displayValueOf(tailValue, mode),
      color: TAIL_COLOR,
    })
  }

  return entries
}
