import { useState } from 'react'
import SegmentedToggle from '../SegmentedToggle/SegmentedToggle'
import {
  TIMEFRAME_OPTIONS,
  timeframeContext,
  type Timeframe,
} from '../../utils/computeStats'
import './TimeframeMetricCard.css'

interface TimeframeMetricCardProps {
  label: string
  unit?: string
  defaultTimeframe?: Timeframe
  loading?: boolean
  error?: boolean
  onRetry?: () => void
  getValue: (timeframe: Timeframe) => { value: string; context: string }
}

export default function TimeframeMetricCard({
  label,
  unit,
  defaultTimeframe = 'week',
  loading,
  error,
  onRetry,
  getValue,
}: TimeframeMetricCardProps) {
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe)
  const computed = error || loading ? null : getValue(timeframe)
  const fallbackContext = timeframeContext(timeframe)

  return (
    <section className="metric-card" aria-label={label}>
      <div className="metric-card-label">{label}</div>
      <SegmentedToggle
        value={timeframe}
        options={TIMEFRAME_OPTIONS}
        onChange={setTimeframe}
        ariaLabel={`${label} timeframe`}
      />
      <div className="metric-card-value-row">
        {loading ? (
          <span className="metric-card-skeleton" aria-hidden="true" />
        ) : error ? (
          <span className="metric-card-value metric-card-value--muted">—</span>
        ) : (
          <>
            <span className="metric-card-value">{computed?.value ?? '—'}</span>
            {unit && <span className="metric-card-unit">{unit}</span>}
          </>
        )}
      </div>
      <div className="metric-card-context">
        {error ? (
          <>
            <span>Unable to load.</span>
            {onRetry && (
              <button
                type="button"
                className="metric-card-retry"
                onClick={onRetry}
              >
                Retry
              </button>
            )}
          </>
        ) : (
          computed?.context ?? fallbackContext
        )}
      </div>
    </section>
  )
}
