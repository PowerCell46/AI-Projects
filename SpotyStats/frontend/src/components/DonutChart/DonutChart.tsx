import { buildSegmentPaths } from './DonutChart.helpers'
import type { DonutSegment } from './DonutChart.helpers'
import styles from './DonutChart.module.css'


interface DonutChartProps {
  segments: DonutSegment[]
  centerValue: string
  centerLabel: string
  hoveredIndex: number | null
  onHover: (index: number | null) => void
}

export const DonutChart = ({
  segments,
  centerValue,
  centerLabel,
  hoveredIndex,
  onHover,
}: DonutChartProps) => (
  <svg viewBox="0 0 100 100" className={styles.chart} role="img" aria-label={`${centerValue} ${centerLabel}`}>
    {buildSegmentPaths(segments).map((segment, index) => (
      <path
        key={index}
        d={segment.path}
        fill={segment.color}
        className={index === hoveredIndex ? styles.segmentHovered : styles.segment}
        onMouseEnter={() => onHover(index)}
        onMouseLeave={() => onHover(null)}
      />
    ))}
    <text x="50" y="49" textAnchor="middle" className={styles.centerValue}>
      {centerValue}
    </text>
    <text x="50" y="57" textAnchor="middle" className={styles.centerLabel}>
      {centerLabel}
    </text>
  </svg>
)
