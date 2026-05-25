import './OverdueCard.css'

interface OverdueCardProps {
  count: number
  loading?: boolean
  error?: boolean
  onRetry?: () => void
}

export default function OverdueCard({
  count,
  loading,
  error,
  onRetry,
}: OverdueCardProps) {
  const showing = !loading && !error
  const isEmpty = showing && count === 0
  const hasOverdue = showing && count > 0
  const unit = count === 1 ? 'task' : 'tasks'

  const description = error
    ? 'Unable to load overdue tasks.'
    : loading
      ? 'Checking your overdue tasks…'
      : hasOverdue
        ? 'These tasks have passed their due date and remain incomplete.'
        : 'Nothing past due. Nice.'

  return (
    <section className="overdue-card" aria-label="Overdue tasks">
      <div className="overdue-card-left">
        <span
          className={`overdue-card-dot${
            hasOverdue ? ' overdue-card-dot--danger' : ''
          }`}
          aria-hidden="true"
        />
        <div className="overdue-card-text">
          <div className="overdue-card-label">Overdue tasks</div>
          <p className="overdue-card-description">
            {description}
            {error && onRetry && (
              <>
                {' '}
                <button
                  type="button"
                  className="overdue-card-retry"
                  onClick={onRetry}
                >
                  Retry
                </button>
              </>
            )}
          </p>
        </div>
      </div>
      <div className="overdue-card-right">
        {loading ? (
          <span className="overdue-card-skeleton" aria-hidden="true" />
        ) : error ? (
          <span className="overdue-card-number overdue-card-number--muted">—</span>
        ) : (
          <>
            <span
              className={`overdue-card-number${
                isEmpty ? ' overdue-card-number--muted' : ''
              }`}
            >
              {count}
            </span>
            <span className="overdue-card-unit">{unit}</span>
          </>
        )}
      </div>
    </section>
  )
}
