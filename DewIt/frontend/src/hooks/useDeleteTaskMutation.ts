import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteTask } from '../api/tasks'
import { tasksQueryKey } from './useTasksQuery'
import { categoriesQueryKey } from './useCategoriesQuery'

export function useDeleteTaskMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteTask(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tasksQueryKey })
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
    },
  })
}
