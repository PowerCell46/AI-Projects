import { request } from './client'
import type {
  Page,
  TaskCreateRequest,
  TaskResponse,
  TaskUpdateRequest,
} from '../types'

export function listTasks(page = 0, size = 200): Promise<Page<TaskResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: 'createdAt,desc',
  })
  return request<Page<TaskResponse>>(`/tasks?${params}`)
}

export function getTask(id: string): Promise<TaskResponse> {
  return request<TaskResponse>(`/tasks/${id}`)
}

export function createTask(body: TaskCreateRequest): Promise<TaskResponse> {
  return request<TaskResponse>('/tasks', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateTask(
  id: string,
  body: TaskUpdateRequest,
): Promise<TaskResponse> {
  return request<TaskResponse>(`/tasks/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export function deleteTask(id: string): Promise<void> {
  return request<void>(`/tasks/${id}`, { method: 'DELETE' })
}
