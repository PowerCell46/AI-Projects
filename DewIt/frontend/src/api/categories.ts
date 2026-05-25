import { request } from './client'
import type { CategoryCreateRequest, CategoryResponse, Page } from '../types'

export function listCategories(page = 0, size = 100): Promise<Page<CategoryResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: 'name,asc',
  })
  return request<Page<CategoryResponse>>(`/categories?${params}`)
}

export function getCategory(id: string): Promise<CategoryResponse> {
  return request<CategoryResponse>(`/categories/${id}`)
}

export function createCategory(body: CategoryCreateRequest): Promise<CategoryResponse> {
  return request<CategoryResponse>('/categories', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateCategory(id: string, body: { name: string }): Promise<CategoryResponse> {
  return request<CategoryResponse>(`/categories/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}
