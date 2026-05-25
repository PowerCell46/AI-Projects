import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateCategory } from '../api/categories'
import { categoriesQueryKey } from './useCategoriesQuery'
import { tasksQueryKey } from './useTasksQuery'

export function useUpdateCategoryMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      updateCategory(id, { name }),
    onSuccess: () => {
      // Invalidate both — task responses embed categoryName, so they need refreshing too.
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
      queryClient.invalidateQueries({ queryKey: tasksQueryKey })
    },
  })
}
