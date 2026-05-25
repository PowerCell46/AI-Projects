import { parseISO, subDays, subMonths } from 'date-fns'
import type { TaskResponse } from '../types'

export type Timeframe = 'day' | 'week' | 'month' | 'year'

export const TIMEFRAME_OPTIONS: { value: Timeframe; label: string }[] = [
  { value: 'day', label: 'Day' },
  { value: 'week', label: 'Week' },
  { value: 'month', label: 'Month' },
  { value: 'year', label: 'Year' },
]

const TIMEFRAME_CONTEXT: Record<Timeframe, string> = {
  day: 'in the last 24 hours',
  week: 'in the last 7 days',
  month: 'in the last 30 days',
  year: 'in the last 12 months',
}

export function timeframeContext(timeframe: Timeframe): string {
  return TIMEFRAME_CONTEXT[timeframe]
}

function timeframeStart(now: Date, timeframe: Timeframe): Date {
  switch (timeframe) {
    case 'day':
      return subDays(now, 1)
    case 'week':
      return subDays(now, 7)
    case 'month':
      return subDays(now, 30)
    case 'year':
      return subMonths(now, 12)
  }
}

function isWithinTimeframe(
  isoDate: string,
  now: Date,
  timeframe: Timeframe,
): boolean {
  const ts = parseISO(isoDate).getTime()
  return ts >= timeframeStart(now, timeframe).getTime() && ts <= now.getTime()
}

export function countCreated(
  tasks: TaskResponse[],
  timeframe: Timeframe,
  now: Date = new Date(),
): number {
  return tasks.reduce(
    (n, t) => (isWithinTimeframe(t.createdAt, now, timeframe) ? n + 1 : n),
    0,
  )
}

export function countCompleted(
  tasks: TaskResponse[],
  timeframe: Timeframe,
  now: Date = new Date(),
): number {
  return tasks.reduce(
    (n, t) =>
      t.status === 'COMPLETED' &&
      t.completedAt != null &&
      isWithinTimeframe(t.completedAt, now, timeframe)
        ? n + 1
        : n,
    0,
  )
}

export function completionRate(
  tasks: TaskResponse[],
  timeframe: Timeframe,
  now: Date = new Date(),
): { done: number; created: number; rate: number } {
  const created = countCreated(tasks, timeframe, now)
  const done = countCompleted(tasks, timeframe, now)
  const rate = created === 0 ? 0 : Math.round((done / created) * 100)
  return { done, created, rate }
}

export function countOverdue(
  tasks: TaskResponse[],
  now: Date = new Date(),
): number {
  const startOfToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime()
  return tasks.reduce((n, t) => {
    if (t.status !== 'ACTIVE' || !t.dueDate) return n
    return parseISO(t.dueDate).getTime() < startOfToday ? n + 1 : n
  }, 0)
}

export interface CategoryStat {
  name: string
  completed: number
}

export function completedByCategory(
  tasks: TaskResponse[],
  now: Date = new Date(),
  limit = 5,
): CategoryStat[] {
  const counts = new Map<string, number>()
  for (const t of tasks) {
    if (t.status !== 'COMPLETED') continue
    if (!t.completedAt || !isWithinTimeframe(t.completedAt, now, 'month')) continue
    const key = t.categoryName || '—'
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return Array.from(counts, ([name, completed]) => ({ name, completed }))
    .sort((a, b) => b.completed - a.completed)
    .slice(0, limit)
}
