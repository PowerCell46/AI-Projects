import type { PlayedTrack } from '../../services/listeningService'
import { formatDuration, formatPlayedAt } from './TrackRow.helpers'
import styles from './TrackRow.module.css'


interface TrackRowProps {
  track: PlayedTrack
  onToggleLiked: (trackId: string) => void
  /** Overrides the played-at time, e.g. with the date a track was liked. */
  timeLabel?: string
}

export const TrackRow = ({ track, onToggleLiked, timeLabel }: TrackRowProps) => (
  <li className={styles.row}>
    {track.albumArtUrl !== null ? (
      <img className={styles.art} src={track.albumArtUrl} alt="" />
    ) : (
      <div className={styles.art} />
    )}
    <div className={styles.titles}>
      <div className={styles.title}>{track.title}</div>
      <div className={styles.subtitle}>
        {track.artist} · {track.album}
      </div>
    </div>
    <div className={styles.meta}>
      <button
        className={track.liked ? styles.heartLiked : styles.heart}
        aria-label={track.liked ? 'Unlike' : 'Like'}
        aria-pressed={track.liked}
        onClick={() => onToggleLiked(track.trackId)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
        </svg>
      </button>
      <span className={styles.time}>{timeLabel ?? formatPlayedAt(track.playedAt)}</span>
      <span className={styles.time}>{formatDuration(track.durationMs)}</span>
    </div>
  </li>
)
