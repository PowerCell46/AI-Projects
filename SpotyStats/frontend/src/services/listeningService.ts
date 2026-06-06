import { apiGet, apiPost } from './api'

export interface StatMetric {
  label: string
  value: string
  sublabel: string
}

export interface PlayedTrack {
  id: string
  trackId: string
  title: string
  artist: string
  album: string
  albumArtUrl: string | null
  playedAt: string
  durationMs: number
  liked: boolean
}

export interface DailyHistory {
  date: string
  tracks: PlayedTrack[]
}

export interface ArtistShare {
  artistName: string
  trackCount: number
  listeningTimeMs: number
}

interface WeekStatsResponse {
  tracksPlayed: number
  tracksPlayedDeltaPercent: number | null
  listeningTimeMs: number
  uniqueArtists: number
  newArtists: number
  uniqueTracks: number
}

const browserZone = (): string => Intl.DateTimeFormat().resolvedOptions().timeZone

const formatListeningTime = (timeMs: number): string => {
  const totalMinutes = Math.round(timeMs / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`
}

const formatDelta = (deltaPercent: number | null): string => {
  if (deltaPercent === null) {
    return 'no plays last week'
  }

  const sign = deltaPercent >= 0 ? '+' : ''
  return `${sign}${deltaPercent}% vs last week`
}

/**
 * Pulls the latest recently-played tracks from Spotify into the backend's
 * database. Call before fetching stats so they reflect up-to-the-minute plays.
 */
export const syncListeningHistory = (): Promise<void> =>
  apiPost('/api/listening/sync')
    .then(() => undefined)

export const fetchWeekStats = async (): Promise<StatMetric[]> => {
  const stats = await apiGet<WeekStatsResponse>('/api/listening/week-stats')

  return [
    {
      label: 'Tracks played',
      value: String(stats.tracksPlayed),
      sublabel: formatDelta(stats.tracksPlayedDeltaPercent),
    },
    {
      label: 'Listening time',
      value: formatListeningTime(stats.listeningTimeMs),
      sublabel: 'across 7 days',
    },
    {
      label: 'Unique artists',
      value: String(stats.uniqueArtists),
      sublabel: `${stats.newArtists} new this week`,
    },
    {
      label: 'Unique tracks',
      value: String(stats.uniqueTracks),
      sublabel: `out of ${stats.tracksPlayed} plays`,
    },
  ]
}

export const fetchTodayHistory = (): Promise<DailyHistory> =>
  apiGet<DailyHistory>(`/api/listening/today?zone=${encodeURIComponent(browserZone())}`)

export const fetchArtistBreakdown = (): Promise<ArtistShare[]> =>
  apiGet<ArtistShare[]>('/api/listening/artist-breakdown')

export const setTrackLiked = (trackId: string, liked: boolean): Promise<void> =>
  apiPost(`/api/listening/tracks/${trackId}/liked`, { liked })
    .then(() => undefined)
