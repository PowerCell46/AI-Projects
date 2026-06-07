/** Shared display formatters used across pages and components. */

export const formatListeningTime = (timeMs: number): string => {
  const totalMinutes = Math.round(timeMs / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`
}

export const formatShortDate = (isoTimestamp: string): string =>
  new Date(isoTimestamp)
    .toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

export const formatWeekLabel = (isoDate: string): string =>
  new Date(`${isoDate}T00:00:00`)
    .toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
