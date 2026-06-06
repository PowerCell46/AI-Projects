export const formatPlayedAt = (isoTimestamp: string): string =>
  new Date(isoTimestamp)
    .toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

export const formatDuration = (durationMs: number): string => {
  const totalSeconds = Math.round(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
