import { useCallback, useEffect, useState } from 'react'
import { ArtistBreakdownPanel } from '../../components/ArtistBreakdownPanel/ArtistBreakdownPanel'
import { Button } from '../../components/Button/Button'
import { HistoryPanel } from '../../components/HistoryPanel/HistoryPanel'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { StatCard, StatCardSkeleton } from '../../components/StatCard/StatCard'
import { useGuardedLoad } from '../../hooks/useGuardedLoad'
import {
  fetchArtistBreakdown,
  fetchHistoryPage,
  fetchStats,
  fetchTodayHistory,
  setTrackLiked,
  syncListeningHistory,
} from '../../services/listeningService'
import type {
  ArtistShare,
  DailyHistory,
  ListeningPeriod,
  StatMetric,
} from '../../services/listeningService'
import styles from './OverviewPage.module.css'


const INITIAL_RANGE: ListeningPeriod = 'today'

/**
 * The diary days for a range: today's single day (always present, possibly
 * empty), or the rolling week's play-bearing days, newest first.
 */
const fetchDiaryDays = (range: ListeningPeriod): Promise<DailyHistory[]> =>
  range === 'today'
    ? fetchTodayHistory().then((day) => [day])
    : fetchHistoryPage().then((page) => page.days)

export const OverviewPage = () => {
  const [stats, setStats] = useState<StatMetric[] | null>(null)
  const [days, setDays] = useState<DailyHistory[] | null>(null)
  const [shares, setShares] = useState<ArtistShare[] | null>(null)
  const [range, setRange] = useState<ListeningPeriod>(INITIAL_RANGE)

  const { run, isRunning } = useGuardedLoad()

  /**
   * Syncs fresh plays from Spotify, then loads all three panels. The guard
   * skips overlapping runs — two concurrent syncs would race each other for
   * the same plays.
   */
  const loadAll = useCallback(
    (period: ListeningPeriod) => {
      run(() => {
        setStats(null)
        setDays(null)
        setShares(null)

        // Pull fresh plays from Spotify first; if that fails, stale data
        // from the database still beats an empty page.
        return syncListeningHistory()
          .catch(() => undefined)
          .then(() =>
            Promise.allSettled([
              fetchStats(period).then(setStats),
              fetchDiaryDays(period).then(setDays),
              fetchArtistBreakdown(period).then(setShares),
            ]),
          )
      })
    },
    [run],
  )

  useEffect(() => {
    loadAll(INITIAL_RANGE)
  }, [loadAll])

  /**
   * Flips all three panels between today and the rolling week. No re-sync —
   * the plays are already fresh from the initial load.
   */
  const switchRange = (next: ListeningPeriod): void => {
    if (next === range || isRunning()) {
      return
    }

    setRange(next)
    setStats(null)
    setDays(null)
    setShares(null)

    fetchStats(next)
      .then(setStats)
      .catch(() => setStats([]))

    fetchDiaryDays(next)
      .then(setDays)
      .catch(() => setDays([]))

    fetchArtistBreakdown(next)
      .then(setShares)
      .catch(() => setShares([]))
  }

  const applyLiked = (trackId: string, liked: boolean): void => {
    setDays((current) => {
      if (current === null) {
        return current
      }

      return current.map((day) => ({
        ...day,
        tracks: day.tracks.map((track) =>
          track.trackId === trackId ? { ...track, liked } : track,
        ),
      }))
    })
  }

  /**
   * Optimistically flips every occurrence of the track across all days,
   * persists to the user's Spotify library, and rolls back if Spotify
   * rejects the change.
   */
  const toggleLiked = (trackId: string): void => {
    const target = days
      ?.flatMap((day) => day.tracks)
      .find((track) => track.trackId === trackId)

    if (target === undefined) {
      return
    }

    const nextLiked = !target.liked

    applyLiked(trackId, nextLiked)
    setTrackLiked(trackId, nextLiked)
      .catch(() => applyLiked(trackId, !nextLiked))
  }

  return (
    <>
      <PageHeader
        eyebrow="Listening diary"
        title="Your week in sound"
        actions={
          <>
            <Button
              variant="secondary"
              selected={range === 'today'}
              onClick={() => switchRange('today')}
            >
              Today
            </Button>
            <Button
              variant="secondary"
              selected={range === 'week'}
              onClick={() => switchRange('week')}
            >
              This week
            </Button>
            <Button variant="primary" onClick={() => loadAll(range)}>
              Refresh
            </Button>
          </>
        }
      />
      <div className={styles.statsRow}>
        {stats === null
          ? [0, 1, 2, 3].map((index) => <StatCardSkeleton key={index} />)
          : stats.map((stat) => (
              <StatCard
                key={stat.label}
                label={stat.label}
                value={stat.value}
                sublabel={stat.sublabel}
              />
            ))}
      </div>
      <div className={styles.contentGrid}>
        <div className={styles.diaryColumn}>
          {days === null && (
            <HistoryPanel history={null} onToggleLiked={() => undefined} />
          )}
          {days !== null && days.length === 0 && (
            <Panel>
              <p className={styles.empty}>
                No plays in the last week — listen to something on Spotify and come back.
              </p>
            </Panel>
          )}
          {days?.map((day) => (
            <HistoryPanel key={day.date} history={day} onToggleLiked={toggleLiked} />
          ))}
        </div>
        <ArtistBreakdownPanel shares={shares} />
      </div>
    </>
  )
}
