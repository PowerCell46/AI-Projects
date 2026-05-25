export type ApiPriority = 'LOW' | 'MEDIUM' | 'HIGH'
export type ApiStatus = 'ACTIVE' | 'COMPLETED'

export interface TaskResponse {
  id: string
  title: string
  description: string | null
  dueDate: string | null
  priority: ApiPriority
  status: ApiStatus
  categoryId: string
  categoryName: string
  createdAt: string
  lastModifiedAt: string
  completedAt: string | null
}

export interface CategoryResponse {
  id: string
  name: string
  createdAt: string
  lastModifiedAt: string
  tasks: TaskResponse[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface TaskCreateRequest {
  title: string
  description?: string
  dueDate?: string | null
  priority: ApiPriority
  status: ApiStatus
  categoryId: string
}

export interface TaskUpdateRequest {
  title?: string
  description?: string
  dueDate?: string | null
  priority?: ApiPriority
  status?: ApiStatus
  categoryId?: string
}

export interface CategoryCreateRequest {
  name: string
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  errors?: { field: string; message: string }[]
}
