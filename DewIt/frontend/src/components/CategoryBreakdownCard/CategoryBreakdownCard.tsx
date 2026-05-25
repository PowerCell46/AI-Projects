import type { CategoryStat } from '../../utils/computeStats'
import './CategoryBreakdownCard.css'

interface CategoryBreakdownCardProps {
  rows: CategoryStat[]
  loading?: boolean
  error?: boolean
  onRetry?: () => void
}

export default function CategoryBreakdownCard({
  rows,
  loading,
  error,
  onRetry,
}: CategoryBreakdownCardProps) {
  const total = rows.reduce((n, r) => n + r.completed, 0)
  const maxValue = rows.reduce((max, r) => Math.max(max, r.completed), 0)

  const ariaLabel =
    rows.length > 0
      ? `Completed by category: ${rows
          .map((r) => `${r.name} ${r.completed}`)
          .join(', ')}`
      : 'Completed by category'

  return (
    <section className="breakdown-card" aria-label="Completed by category">
      <header className="breakdown-card-header">
        <div className="breakdown-card-header-left">
          <div className="breakdown-card-label">Completed by category</div>
          <div className="breakdown-card-subtitle">
            Where your work has gone in the last 30 days
          </div>
        </div>
        {!loading && !error && rows.length > 0 && (
          <div className="breakdown-card-total">{total} total</div>
        )}
      </header>

      {loading ? (
        <div className="breakdown-card-rows" aria-hidden="true">
          {Array.from({ length: 3 }).map((_, i) => (
            <div className="breakdown-row breakdown-row--skeleton" key={i}>
              <div className="breakdown-row-name breakdown-row-name--skeleton" />
              <div className="breakdown-row-track">
                <div className="breakdown-row-fill breakdown-row-fill--skeleton" />
              </div>
              <div className="breakdown-row-count breakdown-row-count--skeleton" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="breakdown-card-error">
          <span>Unable to load.</span>
          {onRetry && (
            <button
              type="button"
              className="breakdown-card-retry"
              onClick={onRetry}
            >
              Retry
            </button>
          )}
        </div>
      ) : rows.length === 0 ? (
        <div className="breakdown-card-empty">
          No completed tasks in the last 30 days.
        </div>
      ) : (
        <div
          className="breakdown-card-rows"
          role="list"
          aria-label={ariaLabel}
        >
          {rows.map((row) => {
            const pct = maxValue === 0 ? 0 : (row.completed / maxValue) * 100
            return (
              <div className="breakdown-row" role="listitem" key={row.name}>
                <div className="breakdown-row-name" title={row.name}>
                  {row.name}
                </div>
                <div className="breakdown-row-track">
                  <div
                    className="breakdown-row-fill"
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <div className="breakdown-row-count">{row.completed}</div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
