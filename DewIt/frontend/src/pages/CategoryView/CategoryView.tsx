import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader/PageHeader'
import TaskCard from '../../components/TaskCard/TaskCard'
import SkeletonCard from '../../components/SkeletonCard/SkeletonCard'
import EditTaskModal from '../../components/EditTaskModal/EditTaskModal'
import ConfirmModal from '../../components/ConfirmModal/ConfirmModal'
import FilterSelect from '../../components/FilterSelect/FilterSelect'
import { useTasksQuery } from '../../hooks/useTasksQuery'
import { useCategoriesQuery } from '../../hooks/useCategoriesQuery'
import { useDeleteTaskMutation } from '../../hooks/useDeleteTaskMutation'
import { useToggleTaskStatusMutation } from '../../hooks/useToggleTaskStatusMutation'
import { useUpdateCategoryMutation } from '../../hooks/useUpdateCategoryMutation'
import type { TaskResponse } from '../../types'
import './CategoryView.css'

type SortOrder = 'dueAsc' | 'dueDesc'
type PriorityFilter = 'all' | 'low' | 'medium' | 'high'
type StatusFilter = 'all' | 'active' | 'completed'

const SORT_OPTIONS: { value: SortOrder; label: string }[] = [
  { value: 'dueAsc', label: 'Due soonest' },
  { value: 'dueDesc', label: 'Due latest' },
]

const PRIORITY_OPTIONS: { value: PriorityFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'low', label: 'Low' },
  { value: 'medium', label: 'Medium' },
  { value: 'high', label: 'High' },
]

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'active', label: 'Active' },
  { value: 'completed', label: 'Completed' },
]

const INITIAL_VISIBLE = 15

function getColumnCount(width: number): number {
  if (width >= 1100) return 5
  if (width >= 768) return 4
  if (width >= 560) return 3
  if (width >= 481) return 2
  return 1
}

function useColumnCount(): number {
  const [cols, setCols] = useState(() =>
    typeof window === 'undefined' ? 5 : getColumnCount(window.innerWidth),
  )
  useEffect(() => {
    const onResize = () => setCols(getColumnCount(window.innerWidth))
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  return cols
}

export default function CategoryView() {
  const { id } = useParams<{ id: string }>()
  const tasksQuery = useTasksQuery()
  const categoriesQuery = useCategoriesQuery()
  const deleteMutation = useDeleteTaskMutation()
  const toggleStatusMutation = useToggleTaskStatusMutation()

  const [sort, setSort] = useState<SortOrder>('dueAsc')
  const [priorityFilter, setPriorityFilter] = useState<PriorityFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [visibleCount, setVisibleCount] = useState(INITIAL_VISIBLE)
  const [selection, setSelection] = useState<
    { task: TaskResponse; mode: 'view' | 'edit' } | null
  >(null)
  const [confirmingDelete, setConfirmingDelete] = useState<TaskResponse | null>(null)

  // Category rename state
  const renameMutation = useUpdateCategoryMutation()
  const [isRenaming, setIsRenaming] = useState(false)
  const [renameValue, setRenameValue] = useState('')
  const renameInputRef = useRef<HTMLInputElement | null>(null)

  const cols = useColumnCount()

  useEffect(() => {
    setVisibleCount(INITIAL_VISIBLE)
  }, [sort, priorityFilter, statusFilter])

  const category = useMemo(
    () => categoriesQuery.data?.content.find((c) => c.id === id) ?? null,
    [categoriesQuery.data, id],
  )

  const filteredSorted = useMemo(() => {
    if (!id) return []
    const all = tasksQuery.data?.content ?? []
    const list = all
      .filter((t) => t.categoryId === id)
      .filter(
        (t) => priorityFilter === 'all' || t.priority.toLowerCase() === priorityFilter,
      )
      .filter(
        (t) => statusFilter === 'all' || t.status.toLowerCase() === statusFilter,
      )

    const dueValue = (t: TaskResponse) =>
      t.dueDate ? new Date(t.dueDate).getTime() : Number.POSITIVE_INFINITY

    list.sort((a, b) => {
      const av = dueValue(a)
      const bv = dueValue(b)
      return sort === 'dueAsc' ? av - bv : bv - av
    })

    return list
  }, [tasksQuery.data, id, priorityFilter, statusFilter, sort])

  const totalCount = filteredSorted.length
  const visibleTasks = filteredSorted.slice(0, visibleCount)
  const gridSlots = Math.ceil(visibleTasks.length / cols) * cols
  const skeletonCount = Math.max(0, gridSlots - visibleTasks.length)

  const handleToggleStatus = (task: TaskResponse) => {
    const nextStatus = task.status === 'COMPLETED' ? 'ACTIVE' : 'COMPLETED'
    toggleStatusMutation.mutate({ id: task.id, nextStatus })
  }

  const handleConfirmDelete = () => {
    if (!confirmingDelete) return
    deleteMutation.mutate(confirmingDelete.id, {
      onSuccess: () => setConfirmingDelete(null),
    })
  }

  const startRename = () => {
    setRenameValue(category?.name ?? '')
    setIsRenaming(true)
    // Focus the input on the next tick once it's mounted
    setTimeout(() => renameInputRef.current?.focus(), 0)
  }

  const cancelRename = () => {
    setIsRenaming(false)
    renameMutation.reset()
  }

  const handleRenameSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = renameValue.trim()
    if (!trimmed || !id) return
    renameMutation.mutate(
      { id, name: trimmed },
      { onSuccess: () => setIsRenaming(false) },
    )
  }

  const isLoading = tasksQuery.isLoading || categoriesQuery.isLoading
  const error = tasksQuery.error || categoriesQuery.error
  const categoryName = category?.name ?? 'Category'

  return (
    <div className="category-view">
      <PageHeader />

      <div className="category-view-title-block">
        <div className="category-view-eyebrow">Category</div>
        <div className="category-view-title-row">
          {isRenaming ? (
            <form className="category-view-rename-form" onSubmit={handleRenameSubmit}>
              <input
                ref={renameInputRef}
                className="category-view-rename-input"
                value={renameValue}
                maxLength={100}
                aria-label="Category name"
                onChange={(e) => setRenameValue(e.target.value)}
                onKeyDown={(e) => e.key === 'Escape' && cancelRename()}
              />
              <div className="category-view-rename-actions">
                <button
                  type="submit"
                  className="category-view-rename-save"
                  disabled={!renameValue.trim() || renameMutation.isPending}
                >
                  {renameMutation.isPending ? 'Saving…' : 'Save'}
                </button>
                <button
                  type="button"
                  className="category-view-rename-cancel"
                  onClick={cancelRename}
                  disabled={renameMutation.isPending}
                >
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <h1 className="category-view-title">{categoryName}</h1>
              {category && (
                <button
                  type="button"
                  className="category-view-rename-btn"
                  aria-label="Rename category"
                  onClick={startRename}
                >
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M11.5 2.5l2 2L5 13H3v-2L11.5 2.5z" />
                    <path d="M10.5 3.5l2 2" />
                  </svg>
                </button>
              )}
            </>
          )}
          {!isRenaming && (
            <span className="category-view-count">
              · {totalCount} {totalCount === 1 ? 'task' : 'tasks'}
            </span>
          )}
        </div>
        {renameMutation.isError && (
          <p className="category-view-rename-error" role="alert">
            Couldn't rename the category. The name may already be taken.
          </p>
        )}
      </div>

      {error ? (
        <div className="category-view-error">
          Couldn’t load tasks. Check the backend connection and refresh.
        </div>
      ) : isLoading ? (
        <div className="category-view-loading">Loading…</div>
      ) : (
        <>
          <div className="category-view-filter-bar">
            <span className="category-view-status">
              Showing {visibleTasks.length} of {totalCount}
            </span>
            <div className="category-view-filters">
              <FilterSelect
                label="Sort"
                value={sort}
                options={SORT_OPTIONS}
                onChange={setSort}
              />
              <FilterSelect
                label="Priority"
                value={priorityFilter}
                options={PRIORITY_OPTIONS}
                onChange={setPriorityFilter}
              />
              <FilterSelect
                label="Status"
                value={statusFilter}
                options={STATUS_OPTIONS}
                onChange={setStatusFilter}
              />
            </div>
          </div>

          {totalCount === 0 ? (
            <div className="category-view-empty" role="status">
              No tasks match the current filters.
            </div>
          ) : (
            <>
              <div className={`category-view-grid category-view-grid--cols-${cols}`}>
                {visibleTasks.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    onView={(t) => setSelection({ task: t, mode: 'view' })}
                    onEdit={(t) => setSelection({ task: t, mode: 'edit' })}
                    onDelete={setConfirmingDelete}
                    onToggleStatus={handleToggleStatus}
                  />
                ))}
                {Array.from({ length: skeletonCount }).map((_, i) => (
                  <SkeletonCard key={`skeleton-${i}`} />
                ))}
              </div>

              {visibleCount < totalCount && (
                <div className="category-view-load-more">
                  <button
                    type="button"
                    className="category-view-load-more-btn"
                    aria-label="Load more tasks"
                    onClick={() => setVisibleCount((c) => c + cols)}
                  >
                    <span className="category-view-load-more-label">Load more</span>
                    <span className="category-view-load-more-remaining">
                      ({totalCount - visibleCount} remaining)
                    </span>
                  </button>
                </div>
              )}
            </>
          )}
        </>
      )}

      <EditTaskModal
        task={selection?.task ?? null}
        open={!!selection}
        mode={selection?.mode ?? 'view'}
        onClose={() => setSelection(null)}
        onSwitchToEdit={
          selection ? () => setSelection({ task: selection.task, mode: 'edit' }) : undefined
        }
      />
      <ConfirmModal
        open={!!confirmingDelete}
        title="Delete task?"
        message={
          confirmingDelete
            ? `Are you sure you want to delete "${confirmingDelete.title}"? This action cannot be undone.`
            : ''
        }
        confirmLabel="Confirm"
        cancelLabel="Discard"
        variant="danger"
        loading={deleteMutation.isPending}
        onConfirm={handleConfirmDelete}
        onClose={() => setConfirmingDelete(null)}
      />
    </div>
  )
}
