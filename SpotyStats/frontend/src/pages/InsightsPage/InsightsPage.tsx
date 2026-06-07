import { useEffect, useState } from 'react'
import { BarChart } from '../../components/BarChart/BarChart'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { Skeleton } from '../../components/Skeleton/Skeleton'
import { useGuardedLoad } from '../../hooks/useGuardedLoad'
import { fetchInsights, syncListeningHistory } from '../../services/listeningService'
import type { Insights } from '../../services/listeningService'
import { formatListeningTime } from '../../utils/format'
import { buildHourlyBars, buildTrendBars, buildWeekdayBars } from './InsightsPage.helpers'
import styles from './InsightsPage.module.css'


const ChartSkeleton = () => <Skeleton height="200px" radius="14px" />

export const InsightsPage = () => {
  const [insights, setInsights] = useState<Insights | null>(null)
  const { run } = useGuardedLoad()

  useEffect(() => {
    run(() =>
      syncListeningHistory()
        .catch(() => undefined)
        .then(() => fetchInsights())
        .then(setInsights)
        .catch(() => undefined),
    )
  }, [run])

  return (
    <>
      <PageHeader eyebrow="Listening diary" title="Insights" />
      <div className={styles.grid}>
        <Panel>
          <h2 className={styles.panelTitle}>Listening clock</h2>
          <p className={styles.panelSubtitle}>Plays by hour of day, all time</p>
          {insights === null
            ? <ChartSkeleton />
            : <BarChart bars={buildHourlyBars(insights.hourlyActivity)} labelEvery={3} />}
        </Panel>
        <Panel>
          <h2 className={styles.panelTitle}>Weekday pattern</h2>
          <p className={styles.panelSubtitle}>Listening time per weekday, all time</p>
          {insights === null
            ? <ChartSkeleton />
            : <BarChart bars={buildWeekdayBars(insights.weekdayActivity)} />}
        </Panel>
        <Panel>
          <h2 className={styles.panelTitle}>Weekly trend</h2>
          <p className={styles.panelSubtitle}>Plays per week, last 8 weeks</p>
          {insights === null
            ? <ChartSkeleton />
            : insights.weeklyTrend.length === 0
              ? <p className={styles.empty}>No plays recorded yet.</p>
              : <BarChart bars={buildTrendBars(insights.weeklyTrend)} />}
        </Panel>
        <Panel>
          <h2 className={styles.panelTitle}>Top tracks</h2>
          <p className={styles.panelSubtitle}>Most played, all time</p>
          {insights === null && <Skeleton height="200px" radius="14px" />}
          {insights !== null && insights.topTracks.length === 0 && (
            <p className={styles.empty}>No plays recorded yet.</p>
          )}
          {insights !== null && insights.topTracks.length > 0 && (
            <ol className={styles.topTracks}>
              {insights.topTracks.map((track, index) => (
                <li key={track.trackId} className={styles.topTrackRow}>
                  <span className={styles.position}>{index + 1}</span>
                  {track.albumArtUrl !== null ? (
                    <img className={styles.art} src={track.albumArtUrl} alt="" />
                  ) : (
                    <div className={styles.art} />
                  )}
                  <span className={styles.titles}>
                    <span className={styles.trackTitle}>{track.title}</span>
                    <span className={styles.trackArtist}>{track.artist}</span>
                  </span>
                  <span className={styles.metric}>
                    {track.playCount} {track.playCount === 1 ? 'play' : 'plays'}
                  </span>
                  <span className={styles.metric}>{formatListeningTime(track.listeningTimeMs)}</span>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>
    </>
  )
}
