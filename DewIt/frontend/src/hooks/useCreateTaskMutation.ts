import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createTask } from '../api/tasks'
import type { TaskCreateRequest } from '../types'
import { tasksQueryKey } from './useTasksQuery'
import { categoriesQueryKey } from './useCategoriesQuery'

export function useCreateTaskMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: TaskCreateRequest) => createTask(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tasksQueryKey })
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
    },
  })
}
