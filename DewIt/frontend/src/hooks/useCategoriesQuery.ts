import { useQuery } from '@tanstack/react-query'
import { listCategories } from '../api/categories'

export const categoriesQueryKey = ['categories'] as const

export function useCategoriesQuery() {
  return useQuery({
    queryKey: categoriesQueryKey,
    queryFn: () => listCategories(0, 100),
  })
}
