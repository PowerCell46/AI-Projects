import { useQuery } from '@tanstack/react-query'
import { listTasks } from '../api/tasks'

// NOTE: The static key ['tasks'] is shared across Dashboard, CategoryView, and Statistics.
// All three pages therefore read from the same cache entry (capped at size=200).
// If per-page filtering, search, or a "completed tasks" view is added, this key must be
// parameterised (e.g. ['tasks', { filter, page }]) and the mutation invalidation updated.
export const tasksQueryKey = ['tasks'] as const

export function useTasksQuery() {
  return useQuery({
    queryKey: tasksQueryKey,
    queryFn: () => listTasks(0, 200),
  })
}
