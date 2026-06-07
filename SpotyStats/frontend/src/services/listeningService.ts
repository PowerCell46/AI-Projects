import { apiGet, apiPost } from './api'
import { formatListeningTime } from '../utils/format'

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

/** The Overview's toggled window: today (browser zone) or the rolling week. */
export type ListeningPeriod = 'today' | 'week'

interface PeriodStatsResponse {
  tracksPlayed: number
  tracksPlayedDeltaPercent: number | null
  listeningTimeMs: number
  uniqueArtists: number
  newArtists: number
  uniqueTracks: number
}

const browserZone = (): string => Intl.DateTimeFormat().resolvedOptions().timeZone

const periodParams = (period: ListeningPeriod): string =>
  new URLSearchParams({ period, zone: browserZone() }).toString()

const formatDelta = (deltaPercent: number | null, period: ListeningPeriod): string => {
  const priorLabel = period === 'today' ? 'yesterday' : 'last week'

  if (deltaPercent === null) {
    return `no plays ${priorLabel}`
  }

  const sign = deltaPercent >= 0 ? '+' : ''
  return `${sign}${deltaPercent}% vs ${priorLabel}`
}

/**
 * Pulls the latest recently-played tracks from Spotify into the backend's
 * database. Call before fetching stats so they reflect up-to-the-minute plays.
 */
export const syncListeningHistory = (): Promise<void> =>
  apiPost('/api/listening/sync')
    .then(() => undefined)

export const fetchStats = async (period: ListeningPeriod): Promise<StatMetric[]> => {
  const stats = await apiGet<PeriodStatsResponse>(`/api/listening/stats?${periodParams(period)}`)
  const today = period === 'today'

  return [
    {
      label: 'Tracks played',
      value: String(stats.tracksPlayed),
      sublabel: formatDelta(stats.tracksPlayedDeltaPercent, period),
    },
    {
      label: 'Listening time',
      value: formatListeningTime(stats.listeningTimeMs),
      sublabel: today ? 'so far today' : 'across 7 days',
    },
    {
      label: 'Unique artists',
      value: String(stats.uniqueArtists),
      sublabel: `${stats.newArtists} new ${today ? 'today' : 'this week'}`,
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

export const fetchArtistBreakdown = (period: ListeningPeriod): Promise<ArtistShare[]> =>
  apiGet<ArtistShare[]>(`/api/listening/artist-breakdown?${periodParams(period)}`)

export const setTrackLiked = (trackId: string, liked: boolean): Promise<void> =>
  apiPost(`/api/listening/tracks/${trackId}/liked`, { liked })
    .then(() => undefined)

export interface HistoryPage {
  days: DailyHistory[]
  nextBefore: string | null
}

/**
 * One diary page: up to a week of play-bearing days ending just before
 * `before` (today's page when omitted). `nextBefore` feeds the next call.
 */
export const fetchHistoryPage = (before?: string): Promise<HistoryPage> => {
  const params = new URLSearchParams({ zone: browserZone() })

  if (before !== undefined) {
    params.set('before', before)
  }

  return apiGet<HistoryPage>(`/api/listening/history?${params.toString()}`)
}

export type RankPeriod = 'week' | 'month' | 'all'

export interface ArtistRank {
  artistId: string
  artistName: string
  imageUrl: string | null
  /** Metrics are null for Spotify's long-term ranking, which only shares the order. */
  playCount: number | null
  listeningTimeMs: number | null
  uniqueTracks: number | null
  followed: boolean
}

export interface ArtistRanking {
  /** Earliest play we ever captured — coverage stops there, null before the first sync. */
  trackedSince: string | null
  artists: ArtistRank[]
}

export const fetchArtistRanking = (period: RankPeriod): Promise<ArtistRanking> =>
  apiGet<ArtistRanking>(`/api/listening/artists?period=${period}`)

export const setArtistFollowed = (artistId: string, followed: boolean): Promise<void> =>
  apiPost(`/api/listening/artists/${artistId}/followed`, { followed })
    .then(() => undefined)

export interface HourlyActivity {
  hour: number
  plays: number
}

export interface WeekdayActivity {
  isoWeekday: number
  plays: number
  listeningTimeMs: number
}

export interface WeeklyTrendPoint {
  weekStart: string
  plays: number
  listeningTimeMs: number
}

export interface TopTrack {
  trackId: string
  title: string
  artist: string
  albumArtUrl: string | null
  playCount: number
  listeningTimeMs: number
}

export interface Insights {
  hourlyActivity: HourlyActivity[]
  weekdayActivity: WeekdayActivity[]
  weeklyTrend: WeeklyTrendPoint[]
  topTracks: TopTrack[]
}

export const fetchInsights = (): Promise<Insights> =>
  apiGet<Insights>(`/api/listening/insights?zone=${encodeURIComponent(browserZone())}`)
