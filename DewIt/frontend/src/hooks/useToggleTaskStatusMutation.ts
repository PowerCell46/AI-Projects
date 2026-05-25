import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateTask } from '../api/tasks'
import type { ApiStatus, Page, TaskResponse } from '../types'
import { tasksQueryKey } from './useTasksQuery'
import { categoriesQueryKey } from './useCategoriesQuery'

interface ToggleVars {
  id: string
  nextStatus: ApiStatus
}

export function useToggleTaskStatusMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, nextStatus }: ToggleVars) =>
      updateTask(id, { status: nextStatus }),
    onMutate: async ({ id, nextStatus }) => {
      await queryClient.cancelQueries({ queryKey: tasksQueryKey })
      const previous = queryClient.getQueryData<Page<TaskResponse>>(tasksQueryKey)
      if (previous) {
        queryClient.setQueryData<Page<TaskResponse>>(tasksQueryKey, {
          ...previous,
          content: previous.content.map((t) =>
            t.id === id ? { ...t, status: nextStatus, lastModifiedAt: new Date().toISOString() } : t,
          ),
        })
      }
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(tasksQueryKey, context.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: tasksQueryKey })
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
    },
  })
}
