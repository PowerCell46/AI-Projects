import type { ChartBar } from '../../components/BarChart/BarChart'
import type {
  HourlyActivity,
  WeekdayActivity,
  WeeklyTrendPoint,
} from '../../services/listeningService'
import { formatListeningTime, formatWeekLabel } from '../../utils/format'


const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const

const plural = (count: number, noun: string): string =>
  `${count} ${noun}${count === 1 ? '' : 's'}`

/** All 24 hours in order; hours without plays become zero-height bars. */
export const buildHourlyBars = (activity: HourlyActivity[]): ChartBar[] => {
  const playsByHour = new Map(activity.map((entry) => [entry.hour, entry.plays]))

  return Array.from({ length: 24 }, (ignored, hour) => {
    const plays = playsByHour.get(hour) ?? 0

    return {
      label: String(hour),
      value: plays,
      tooltip: `${hour}:00 — ${plural(plays, 'play')}`,
    }
  })
}

/** Monday-first week; weekdays without plays become zero-height bars. */
export const buildWeekdayBars = (activity: WeekdayActivity[]): ChartBar[] => {
  const byWeekday = new Map(activity.map((entry) => [entry.isoWeekday, entry]))

  return WEEKDAY_LABELS.map((label, index) => {
    const entry = byWeekday.get(index + 1)

    return {
      label,
      value: entry?.listeningTimeMs ?? 0,
      tooltip: entry
        ? `${label}: ${formatListeningTime(entry.listeningTimeMs)} · ${plural(entry.plays, 'play')}`
        : `${label}: no plays`,
    }
  })
}

export const buildTrendBars = (trend: WeeklyTrendPoint[]): ChartBar[] =>
  trend.map((week) => ({
    label: formatWeekLabel(week.weekStart),
    value: week.plays,
    tooltip: `Week of ${formatWeekLabel(week.weekStart)}: ${plural(week.plays, 'play')} · ${formatListeningTime(week.listeningTimeMs)}`,
  }))
