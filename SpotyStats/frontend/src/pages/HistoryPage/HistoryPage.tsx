import { useEffect, useState } from 'react'
import { Button } from '../../components/Button/Button'
import { HistoryPanel } from '../../components/HistoryPanel/HistoryPanel'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { useGuardedLoad } from '../../hooks/useGuardedLoad'
import {
  fetchHistoryPage,
  setTrackLiked,
  syncListeningHistory,
} from '../../services/listeningService'
import type { DailyHistory } from '../../services/listeningService'
import styles from './HistoryPage.module.css'


export const HistoryPage = () => {
  const [days, setDays] = useState<DailyHistory[] | null>(null)
  const [nextBefore, setNextBefore] = useState<string | null>(null)
  const [loadingEarlier, setLoadingEarlier] = useState(false)
  const { run } = useGuardedLoad()

  useEffect(() => {
    run(() =>
      syncListeningHistory()
        .catch(() => undefined)
        .then(() => fetchHistoryPage())
        .then((page) => {
          setDays(page.days)
          setNextBefore(page.nextBefore)
        })
        .catch(() => setDays([])),
    )
  }, [run])

  const loadEarlier = (): void => {
    if (nextBefore === null || loadingEarlier) {
      return
    }
    setLoadingEarlier(true)

    fetchHistoryPage(nextBefore)
      .then((page) => {
        setDays((current) => [...(current ?? []), ...page.days])
        setNextBefore(page.nextBefore)
      })
      .catch(() => undefined)
      .then(() => setLoadingEarlier(false))
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
   * persists to Spotify, and rolls back on failure.
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
      <PageHeader eyebrow="Listening diary" title="History" />
      <div className={styles.daysColumn}>
        {days === null && <HistoryPanel history={null} onToggleLiked={() => undefined} />}
        {days !== null && days.length === 0 && (
          <Panel>
            <p className={styles.empty}>
              No plays recorded yet — listen to something on Spotify and come back.
            </p>
          </Panel>
        )}
        {days?.map((day) => (
          <HistoryPanel key={day.date} history={day} onToggleLiked={toggleLiked} />
        ))}
      </div>
      {nextBefore !== null && (
        <div className={styles.loadEarlierRow}>
          <Button variant="secondary" onClick={loadEarlier}>
            {loadingEarlier ? 'Loading…' : 'Load earlier'}
          </Button>
        </div>
      )}
    </>
  )
}
