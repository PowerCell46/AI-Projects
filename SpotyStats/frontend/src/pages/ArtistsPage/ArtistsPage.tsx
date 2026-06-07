import { useEffect, useState } from 'react'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { SegmentedToggle } from '../../components/SegmentedToggle/SegmentedToggle'
import { Skeleton } from '../../components/Skeleton/Skeleton'
import { fetchArtistRanking, setArtistFollowed } from '../../services/listeningService'
import type { ArtistRank, RankPeriod } from '../../services/listeningService'
import { formatListeningTime } from '../../utils/format'
import styles from './ArtistsPage.module.css'


const PERIOD_LABELS = ['7 days', '30 days', 'All time'] as const

type PeriodLabel = (typeof PERIOD_LABELS)[number]

const PERIOD_BY_LABEL: Record<PeriodLabel, RankPeriod> = {
  '7 days': 'week',
  '30 days': 'month',
  'All time': 'all',
}

const PERIOD_WINDOW_DAYS: Record<Exclude<RankPeriod, 'all'>, number> = {
  week: 7,
  month: 30,
}

const DAY_MS = 24 * 60 * 60 * 1000

/** Mirrors the backend's ranking limit (and Spotify's own top-artists cap). */
const RANKING_LIMIT = 50

/**
 * Whether the selected rolling window reaches further back than our captured
 * data. Spotify only exposes the most recent plays, so anything before the
 * first sync is invisible to us — the ranking can't include it. All time is
 * exempt: it comes from Spotify's own long-term ranking.
 */
const reachesBeforeTracking = (period: RankPeriod, trackedSince: string | null): boolean => {
  if (trackedSince === null || period === 'all') {
    return false
  }

  return Date.parse(trackedSince) > Date.now() - PERIOD_WINDOW_DAYS[period] * DAY_MS
}

const formatTrackedSince = (trackedSince: string): string =>
  new Date(trackedSince)
    .toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })

export const ArtistsPage = () => {
  const [periodLabel, setPeriodLabel] = useState<PeriodLabel>('7 days')
  const [ranks, setRanks] = useState<ArtistRank[] | null>(null)
  const [trackedSince, setTrackedSince] = useState<string | null>(null)

  useEffect(() => {
    setRanks(null)

    fetchArtistRanking(PERIOD_BY_LABEL[periodLabel])
      .then((ranking) => {
        setRanks(ranking.artists)
        setTrackedSince(ranking.trackedSince)
      })
      .catch(() => setRanks([]))
  }, [periodLabel])

  const applyFollowed = (artistId: string, followed: boolean): void => {
    setRanks((current) => {
      if (current === null) {
        return current
      }

      return current.map((rank) =>
        rank.artistId === artistId ? { ...rank, followed } : rank,
      )
    })
  }

  /**
   * Optimistically flips the follow state, persists to the user's Spotify
   * account, and rolls back if Spotify rejects the change.
   */
  const toggleFollowed = (artistId: string): void => {
    const target = ranks?.find((rank) => rank.artistId === artistId)

    if (target === undefined) {
      return
    }

    const nextFollowed = !target.followed

    applyFollowed(artistId, nextFollowed)
    setArtistFollowed(artistId, nextFollowed)
      .catch(() => applyFollowed(artistId, !nextFollowed))
  }

  return (
    <>
      <PageHeader
        eyebrow="Listening diary"
        title={`Your top ${RANKING_LIMIT} artists`}
        actions={
          <SegmentedToggle
            options={PERIOD_LABELS}
            value={periodLabel}
            onChange={setPeriodLabel}
          />
        }
      />
      <Panel>
        {ranks !== null &&
          trackedSince !== null &&
          reachesBeforeTracking(PERIOD_BY_LABEL[periodLabel], trackedSince) && (
            <p className={styles.coverageNote}>
              Covers your listening since {formatTrackedSince(trackedSince)} — Spotify only
              shares recent plays, so earlier history isn&apos;t available.
            </p>
          )}
        {ranks !== null && ranks.length > 0 && PERIOD_BY_LABEL[periodLabel] === 'all' && (
          <p className={styles.coverageNote}>
            Ranked by Spotify across your whole listening history — play counts and
            listening time aren&apos;t shared, so only the order is shown.
          </p>
        )}
        {ranks === null && (
          <ul className={styles.list}>
            {[0, 1, 2, 3, 4, 5, 6, 7].map((index) => (
              <li key={index}>
                <Skeleton height="88px" radius="14px" />
              </li>
            ))}
          </ul>
        )}
        {ranks !== null && ranks.length === 0 && (
          <p className={styles.empty}>No plays in this period yet.</p>
        )}
        {ranks !== null && ranks.length > 0 && (
          <ul className={styles.list}>
            {ranks.map((rank, index) => (
              <li
                key={rank.artistId}
                className={rank.playCount === null ? styles.rowOrderOnly : styles.row}
              >
                <span className={styles.position}>{index + 1}</span>
                {rank.imageUrl !== null ? (
                  <img className={styles.portrait} src={rank.imageUrl} alt="" />
                ) : (
                  <span className={styles.avatar} aria-hidden="true">
                    {rank.artistName.charAt(0).toUpperCase()}
                  </span>
                )}
                <span className={styles.name}>{rank.artistName}</span>
                {rank.playCount !== null && (
                  <span className={styles.metric}>
                    {rank.playCount} {rank.playCount === 1 ? 'play' : 'plays'}
                  </span>
                )}
                {rank.uniqueTracks !== null && (
                  <span className={styles.metric}>
                    {rank.uniqueTracks} {rank.uniqueTracks === 1 ? 'track' : 'tracks'}
                  </span>
                )}
                {rank.listeningTimeMs !== null && (
                  <span className={styles.metric}>{formatListeningTime(rank.listeningTimeMs)}</span>
                )}
                <button
                  className={rank.followed ? styles.following : styles.follow}
                  aria-pressed={rank.followed}
                  onClick={() => toggleFollowed(rank.artistId)}
                >
                  {rank.followed ? 'Following' : 'Follow'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}
