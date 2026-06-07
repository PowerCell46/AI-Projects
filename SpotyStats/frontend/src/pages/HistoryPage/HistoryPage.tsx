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
import { formatWeekLabel } from '../../utils/format'
import styles from './HistoryPage.module.css'


/** "Jun 1 – Jun 7" for the page's days (newest first), single date for one day. */
const pageRangeLabel = (days: DailyHistory[]): string => {
  const newest = formatWeekLabel(days[0].date)
  const oldest = formatWeekLabel(days[days.length - 1].date)

  return newest === oldest ? newest : `${oldest} – ${newest}`
}

export const HistoryPage = () => {
  const [days, setDays] = useState<DailyHistory[] | null>(null)
  const [nextBefore, setNextBefore] = useState<string | null>(null)
  /** `before` cursors of the pages navigated into; last one is the current page. */
  const [cursorTrail, setCursorTrail] = useState<string[]>([])
  const [loadingPage, setLoadingPage] = useState(false)
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

  /** Replaces the visible page; `before` undefined loads the newest page. */
  const loadPage = (before?: string): void => {
    setLoadingPage(true)
    setDays(null)

    fetchHistoryPage(before)
      .then((page) => {
        setDays(page.days)
        setNextBefore(page.nextBefore)
      })
      .catch(() => setDays([]))
      .then(() => setLoadingPage(false))
  }

  const showOlder = (): void => {
    if (nextBefore === null || loadingPage) {
      return
    }

    setCursorTrail((trail) => [...trail, nextBefore])
    loadPage(nextBefore)
  }

  const showNewer = (): void => {
    if (cursorTrail.length === 0 || loadingPage) {
      return
    }

    const trail = cursorTrail.slice(0, -1)

    setCursorTrail(trail)
    loadPage(trail[trail.length - 1])
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
        {days !== null && days.length === 0 && cursorTrail.length === 0 && (
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
      {days !== null && (days.length > 0 || cursorTrail.length > 0) && (
        <div className={styles.pager}>
          <span className={styles.pageInfo}>
            {days.length > 0 ? pageRangeLabel(days) : 'Nothing here'}
            {nextBefore === null && ' · start of your history'}
          </span>
          <div className={styles.pagerButtons}>
            {cursorTrail.length > 0 && (
              <Button variant="secondary" onClick={showNewer}>
                Newer
              </Button>
            )}
            {nextBefore !== null && (
              <Button variant="secondary" onClick={showOlder}>
                Older
              </Button>
            )}
          </div>
        </div>
      )}
    </>
  )
}
