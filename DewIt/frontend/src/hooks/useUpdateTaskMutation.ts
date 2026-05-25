import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateTask } from '../api/tasks'
import type { TaskUpdateRequest } from '../types'
import { tasksQueryKey } from './useTasksQuery'
import { categoriesQueryKey } from './useCategoriesQuery'

export function useUpdateTaskMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: TaskUpdateRequest }) =>
      updateTask(id, patch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tasksQueryKey })
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
    },
  })
}
