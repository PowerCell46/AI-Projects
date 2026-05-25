import { endOfToday, isToday, parseISO, startOfToday } from 'date-fns'
import type { TaskResponse } from '../types'

export interface TaskBuckets {
  today: TaskResponse[]
  overdue: TaskResponse[]
  upcoming: TaskResponse[]
}

export function bucketTasks(tasks: TaskResponse[]): TaskBuckets {
  const buckets: TaskBuckets = { today: [], overdue: [], upcoming: [] }
  const dayStart = startOfToday()
  const dayEnd = endOfToday()

  for (const task of tasks) {
    if (task.status === 'COMPLETED') continue
    if (!task.dueDate) continue
    const due = parseISO(task.dueDate)
    if (isToday(due)) {
      buckets.today.push(task)
    } else if (due.getTime() < dayStart.getTime()) {
      buckets.overdue.push(task)
    } else if (due.getTime() > dayEnd.getTime()) {
      buckets.upcoming.push(task)
    }
  }

  const byDue = (a: TaskResponse, b: TaskResponse) =>
    parseISO(a.dueDate!).getTime() - parseISO(b.dueDate!).getTime()
  buckets.today.sort(byDue)
  buckets.overdue.sort(byDue)
  buckets.upcoming.sort(byDue)

  return buckets
}
