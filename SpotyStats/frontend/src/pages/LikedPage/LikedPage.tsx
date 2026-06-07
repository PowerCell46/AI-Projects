import { useEffect, useState } from 'react'
import { Button } from '../../components/Button/Button'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { Skeleton } from '../../components/Skeleton/Skeleton'
import { TrackRow } from '../../components/TrackRow/TrackRow'
import { LIKED_PAGE_SIZE, fetchLikedPage } from '../../services/likedService'
import type { LikedTrack } from '../../services/likedService'
import { setTrackLiked } from '../../services/listeningService'
import type { PlayedTrack } from '../../services/listeningService'
import { formatShortDate } from '../../utils/format'
import styles from './LikedPage.module.css'


interface LikedRow extends LikedTrack {
  liked: boolean
}

/** Adapts a liked-library row to the shape TrackRow renders. */
const toPlayedTrack = (row: LikedRow): PlayedTrack => ({
  id: row.trackId,
  trackId: row.trackId,
  title: row.title,
  artist: row.artist,
  album: row.album,
  albumArtUrl: row.albumArtUrl,
  playedAt: row.addedAt,
  durationMs: row.durationMs,
  liked: row.liked,
})

export const LikedPage = () => {
  const [rows, setRows] = useState<LikedRow[] | null>(null)
  const [total, setTotal] = useState(0)
  const [offset, setOffset] = useState(0)

  useEffect(() => {
    setRows(null)

    fetchLikedPage(offset)
      .then((page) => {
        setRows(page.items.map((item) => ({ ...item, liked: true })))
        setTotal(page.total)
      })
      .catch(() => setRows([]))
  }, [offset])

  const applyLiked = (trackId: string, liked: boolean): void => {
    setRows((current) =>
      current === null
        ? current
        : current.map((row) => (row.trackId === trackId ? { ...row, liked } : row)),
    )
  }

  /**
   * Unliking keeps the row visible (heart goes grey) so the action is easy to
   * undo; the row disappears naturally on the next page load.
   */
  const toggleLiked = (trackId: string): void => {
    const target = rows?.find((row) => row.trackId === trackId)

    if (target === undefined) {
      return
    }

    const nextLiked = !target.liked

    applyLiked(trackId, nextLiked)
    setTrackLiked(trackId, nextLiked)
      .catch(() => applyLiked(trackId, !nextLiked))
  }

  const rangeStart = offset + 1
  const rangeEnd = offset + (rows?.length ?? 0)
  const hasPrevious = offset > 0
  const hasNext = offset + LIKED_PAGE_SIZE < total

  return (
    <>
      <PageHeader eyebrow="Library" title="Liked songs" />
      <Panel>
        {rows === null && (
          <ul className={styles.list}>
            {[0, 1, 2, 3, 4, 5, 6, 7].map((index) => (
              <li key={index}>
                <Skeleton height="88px" radius="14px" />
              </li>
            ))}
          </ul>
        )}
        {rows !== null && rows.length === 0 && (
          <p className={styles.empty}>No liked songs yet.</p>
        )}
        {rows !== null && rows.length > 0 && (
          <>
            <ul className={styles.list}>
              {rows.map((row) => (
                <TrackRow
                  key={row.trackId}
                  track={toPlayedTrack(row)}
                  onToggleLiked={toggleLiked}
                  timeLabel={formatShortDate(row.addedAt)}
                />
              ))}
            </ul>
            <div className={styles.pager}>
              <span className={styles.pageInfo}>
                {rangeStart}–{rangeEnd} of {total}
              </span>
              <div className={styles.pagerButtons}>
                {hasPrevious && (
                  <Button
                    variant="secondary"
                    onClick={() => setOffset(Math.max(offset - LIKED_PAGE_SIZE, 0))}
                  >
                    Previous
                  </Button>
                )}
                {hasNext && (
                  <Button
                    variant="secondary"
                    onClick={() => setOffset(offset + LIKED_PAGE_SIZE)}
                  >
                    Next
                  </Button>
                )}
              </div>
            </div>
          </>
        )}
      </Panel>
    </>
  )
}
