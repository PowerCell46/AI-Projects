import styles from './BarChart.module.css'


export interface ChartBar {
  label: string
  value: number
  tooltip?: string
}

interface BarChartProps {
  bars: ChartBar[]
  /** Render every n-th axis label; 1 (default) labels every bar. */
  labelEvery?: number
}

/**
 * Minimal vertical bar chart: bar heights are relative to the largest value,
 * with hover tooltips via the native title attribute.
 */
export const BarChart = ({ bars, labelEvery = 1 }: BarChartProps) => {
  const maxValue = Math.max(...bars.map((bar) => bar.value), 1)

  return (
    <div className={styles.chart}>
      {bars.map((bar, index) => (
        <div key={bar.label} className={styles.column} title={bar.tooltip ?? `${bar.label}: ${bar.value}`}>
          <div className={styles.barTrack}>
            <div
              className={bar.value > 0 ? styles.bar : styles.barEmpty}
              style={{ height: `${(bar.value / maxValue) * 100}%` }}
            />
          </div>
          <span className={styles.label}>
            {index % labelEvery === 0 ? bar.label : ' '}
          </span>
        </div>
      ))}
    </div>
  )
}
