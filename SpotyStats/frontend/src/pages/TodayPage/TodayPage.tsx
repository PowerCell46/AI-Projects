import { useCallback, useEffect, useRef, useState } from 'react'
import { ArtistBreakdownPanel } from '../../components/ArtistBreakdownPanel/ArtistBreakdownPanel'
import { Button } from '../../components/Button/Button'
import { HistoryPanel } from '../../components/HistoryPanel/HistoryPanel'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { StatCard, StatCardSkeleton } from '../../components/StatCard/StatCard'
import {
  fetchArtistBreakdown,
  fetchTodayHistory,
  fetchWeekStats,
  setTrackLiked,
  syncListeningHistory,
} from '../../services/listeningService'
import type {
  ArtistShare,
  DailyHistory,
  StatMetric,
} from '../../services/listeningService'
import styles from './TodayPage.module.css'


export const TodayPage = () => {
  const [stats, setStats] = useState<StatMetric[] | null>(null)
  const [history, setHistory] = useState<DailyHistory | null>(null)
  const [shares, setShares] = useState<ArtistShare[] | null>(null)

  const isLoadingRef = useRef(false)

  /**
   * Syncs fresh plays from Spotify, then loads all three panels. Guarded
   * against overlapping runs — StrictMode mounts effects twice in dev, and
   * two concurrent syncs would race each other for the same plays.
   */
  const loadAll = useCallback(() => {
    if (isLoadingRef.current) {
      return
    }
    isLoadingRef.current = true

    setStats(null)
    setHistory(null)
    setShares(null)

    // Pull fresh plays from Spotify first; if that fails, stale data
    // from the database still beats an empty page.
    syncListeningHistory()
      .catch(() => undefined)
      .then(() =>
        Promise.allSettled([
          fetchWeekStats().then(setStats),
          fetchTodayHistory().then(setHistory),
          fetchArtistBreakdown().then(setShares),
        ]),
      )
      .then(() => {
        isLoadingRef.current = false
      })
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const applyLiked = (trackId: string, liked: boolean): void => {
    setHistory((current) => {
      if (current === null) {
        return current
      }

      return {
        ...current,
        tracks: current.tracks.map((track) =>
          track.trackId === trackId ? { ...track, liked } : track,
        ),
      }
    })
  }

  /**
   * Optimistically flips the heart, persists to the user's Spotify library,
   * and rolls back if Spotify rejects the change.
   */
  const toggleLiked = (trackId: string): void => {
    const target = history?.tracks.find((track) => track.trackId === trackId)

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
            <Button variant="secondary">This week</Button>
            <Button variant="primary" onClick={loadAll}>
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
        <HistoryPanel history={history} onToggleLiked={toggleLiked} />
        <ArtistBreakdownPanel shares={shares} />
      </div>
    </>
  )
}
