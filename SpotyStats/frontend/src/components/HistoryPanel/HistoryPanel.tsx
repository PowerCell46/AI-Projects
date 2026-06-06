import { Panel } from '../Panel/Panel'
import { Skeleton } from '../Skeleton/Skeleton'
import { TrackRow } from '../TrackRow/TrackRow'
import type { DailyHistory } from '../../services/listeningService'
import styles from './HistoryPanel.module.css'


interface HistoryPanelProps {
  history: DailyHistory | null
  onToggleLiked: (trackId: string) => void
}

const formatDayTitle = (isoDate: string): string =>
  new Date(`${isoDate}T00:00:00`)
    .toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })

export const HistoryPanel = ({ history, onToggleLiked }: HistoryPanelProps) => {
  if (history === null) {
    return (
      <Panel>
        <div className={styles.header}>
          <Skeleton width="180px" height="18px" />
        </div>
        <ul className={styles.list}>
          {[0, 1, 2, 3, 4].map((index) => (
            <li key={index}>
              <Skeleton height="88px" radius="14px" />
            </li>
          ))}
        </ul>
      </Panel>
    )
  }

  return (
    <Panel>
      <div className={styles.header}>
        <h2 className={styles.title}>{formatDayTitle(history.date)}</h2>
        <span className={styles.count}>{history.tracks.length} tracks</span>
      </div>
      <ul className={styles.list}>
        {history.tracks.map((track) => (
          <TrackRow key={track.id} track={track} onToggleLiked={onToggleLiked} />
        ))}
      </ul>
    </Panel>
  )
}
