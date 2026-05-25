import { useMemo } from 'react'
import PageHeader from '../../components/PageHeader/PageHeader'
import TimeframeMetricCard from '../../components/TimeframeMetricCard/TimeframeMetricCard'
import OverdueCard from '../../components/OverdueCard/OverdueCard'
import CategoryBreakdownCard from '../../components/CategoryBreakdownCard/CategoryBreakdownCard'
import { useTasksQuery } from '../../hooks/useTasksQuery'
import {
  completedByCategory,
  completionRate,
  countCompleted,
  countCreated,
  countOverdue,
  timeframeContext,
  type Timeframe,
} from '../../utils/computeStats'
import './Statistics.css'

export default function Statistics() {
  const tasksQuery = useTasksQuery()
  const tasks = useMemo(() => tasksQuery.data?.content ?? [], [tasksQuery.data])
  const loading = tasksQuery.isLoading
  const error = !!tasksQuery.error
  const handleRetry = () => {
    tasksQuery.refetch()
  }

  const completedGetter = (tf: Timeframe) => ({
    value: String(countCompleted(tasks, tf)),
    context: timeframeContext(tf),
  })

  const createdGetter = (tf: Timeframe) => ({
    value: String(countCreated(tasks, tf)),
    context: timeframeContext(tf),
  })

  const rateGetter = (tf: Timeframe) => {
    const { done, created, rate } = completionRate(tasks, tf)
    return {
      value: `${rate}%`,
      context: `${done} of ${created} created`,
    }
  }

  const overdueCount = useMemo(() => countOverdue(tasks), [tasks])
  const breakdownRows = useMemo(() => completedByCategory(tasks), [tasks])

  return (
    <div className="statistics-view">
      <PageHeader />

      <div className="statistics-title-block">
        <div className="statistics-eyebrow">Overview</div>
        <h1 className="statistics-title">Statistics</h1>
      </div>

      <div className="statistics-metrics-row">
        <TimeframeMetricCard
          label="Completed tasks"
          unit="tasks"
          loading={loading}
          error={error}
          onRetry={handleRetry}
          getValue={completedGetter}
        />
        <TimeframeMetricCard
          label="Created tasks"
          unit="tasks"
          loading={loading}
          error={error}
          onRetry={handleRetry}
          getValue={createdGetter}
        />
        <TimeframeMetricCard
          label="Completion rate"
          loading={loading}
          error={error}
          onRetry={handleRetry}
          getValue={rateGetter}
        />
      </div>

      <OverdueCard
        count={overdueCount}
        loading={loading}
        error={error}
        onRetry={handleRetry}
      />

      <CategoryBreakdownCard
        rows={breakdownRows}
        loading={loading}
        error={error}
        onRetry={handleRetry}
      />
    </div>
  )
}
