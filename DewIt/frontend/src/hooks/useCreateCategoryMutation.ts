import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createCategory } from '../api/categories'
import type { CategoryCreateRequest } from '../types'
import { categoriesQueryKey } from './useCategoriesQuery'

export function useCreateCategoryMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CategoryCreateRequest) => createCategory(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoriesQueryKey })
    },
  })
}
