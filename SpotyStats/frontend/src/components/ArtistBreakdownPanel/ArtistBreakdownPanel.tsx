import { useState } from 'react'
import { DonutChart } from '../DonutChart/DonutChart'
import { Panel } from '../Panel/Panel'
import { SegmentedToggle } from '../SegmentedToggle/SegmentedToggle'
import { Skeleton } from '../Skeleton/Skeleton'
import { buildLegendEntries } from './ArtistBreakdownPanel.helpers'
import type { BreakdownMode } from './ArtistBreakdownPanel.helpers'
import { formatListeningTime } from '../../utils/format'
import type { ArtistShare } from '../../services/listeningService'
import styles from './ArtistBreakdownPanel.module.css'


interface ArtistBreakdownPanelProps {
  shares: ArtistShare[] | null
}

const MODES = ['Tracks', 'Time'] as const

export const ArtistBreakdownPanel = ({ shares }: ArtistBreakdownPanelProps) => {
  const [mode, setMode] = useState<BreakdownMode>('Tracks')
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)

  if (shares === null) {
    return (
      <Panel>
        <div className={styles.header}>
          <Skeleton width="80px" height="18px" />
        </div>
        <div className={styles.skeletonChart}>
          <Skeleton width="200px" height="200px" radius="50%" />
        </div>
        <div className={styles.legend}>
          {[0, 1, 2, 3, 4, 5].map((index) => (
            <Skeleton key={index} height="14px" />
          ))}
        </div>
      </Panel>
    )
  }

  const entries = buildLegendEntries(shares, mode)
  const total = entries.reduce((sum, entry) => sum + entry.value, 0)
  const centerValue = mode === 'Tracks' ? String(total) : formatListeningTime(total)
  const centerLabel = mode === 'Tracks' ? 'tracks' : 'listened'

  return (
    <Panel>
      <div className={styles.header}>
        <h2 className={styles.title}>By artist</h2>
        <SegmentedToggle options={MODES} value={mode} onChange={setMode} />
      </div>
      <DonutChart
        segments={entries.map((entry) => ({ value: entry.value, color: entry.color }))}
        centerValue={centerValue}
        centerLabel={centerLabel}
        hoveredIndex={hoveredIndex}
        onHover={setHoveredIndex}
      />
      <ul className={styles.legend}>
        {entries.map((entry, index) => (
          <li
            key={entry.name}
            className={index === hoveredIndex ? styles.legendRowHovered : styles.legendRow}
            onMouseEnter={() => setHoveredIndex(index)}
            onMouseLeave={() => setHoveredIndex(null)}
          >
            <span className={styles.dot} style={{ background: entry.color }} />
            <span className={styles.name}>{entry.name}</span>
            <span className={styles.value}>{entry.displayValue}</span>
          </li>
        ))}
      </ul>
    </Panel>
  )
}
