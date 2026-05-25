import { useMemo, useState } from 'react'
import BrandHeader from '../../components/BrandHeader/BrandHeader'
import CategoriesCarousel from '../../components/CategoriesCarousel/CategoriesCarousel'
import TaskSection from '../../components/TaskSection/TaskSection'
import FloatingAddButton from '../../components/FloatingAddButton/FloatingAddButton'
import EditTaskModal from '../../components/EditTaskModal/EditTaskModal.tsx'
import ConfirmModal from '../../components/ConfirmModal/ConfirmModal'
import { useTasksQuery } from '../../hooks/useTasksQuery'
import { useCategoriesQuery } from '../../hooks/useCategoriesQuery'
import { useDeleteTaskMutation } from '../../hooks/useDeleteTaskMutation'
import { useToggleTaskStatusMutation } from '../../hooks/useToggleTaskStatusMutation'
import { bucketTasks } from '../../utils/bucketTasks'
import type { TaskResponse } from '../../types'
import './Dashboard.css'

export default function Dashboard() {
  const tasksQuery = useTasksQuery()
  const categoriesQuery = useCategoriesQuery()
  const deleteMutation = useDeleteTaskMutation()
  const toggleStatusMutation = useToggleTaskStatusMutation()
  const [selection, setSelection] = useState<
    { task: TaskResponse; mode: 'view' | 'edit' } | null
  >(null)
  const [confirmingDelete, setConfirmingDelete] = useState<TaskResponse | null>(null)

  const handleToggleStatus = (task: TaskResponse) => {
    const nextStatus = task.status === 'COMPLETED' ? 'ACTIVE' : 'COMPLETED'
    toggleStatusMutation.mutate({ id: task.id, nextStatus })
  }

  const modalOpen = !!selection || !!confirmingDelete

  const buckets = useMemo(
    () => bucketTasks(tasksQuery.data?.content ?? []),
    [tasksQuery.data],
  )

  const categories = categoriesQuery.data?.content ?? []

  const handleConfirmDelete = () => {
    if (!confirmingDelete) return
    deleteMutation.mutate(confirmingDelete.id, {
      onSuccess: () => setConfirmingDelete(null),
    })
  }

  const isLoading = tasksQuery.isLoading || categoriesQuery.isLoading
  const error = tasksQuery.error || categoriesQuery.error

  return (
    <div className="dashboard">
      <BrandHeader />

      {error ? (
        <div className="dashboard-error">
          Couldn’t load your tasks. Check the backend connection and refresh.
        </div>
      ) : isLoading ? (
        <div className="dashboard-loading">Loading…</div>
      ) : (
        <>
          <CategoriesCarousel categories={categories} />
          <TaskSection
            variant="today"
            title="Today"
            tasks={buckets.today}
            onView={(task) => setSelection({ task, mode: 'view' })}
            onEdit={(task) => setSelection({ task, mode: 'edit' })}
            onDelete={setConfirmingDelete}
            onToggleStatus={handleToggleStatus}
            paused={modalOpen}
          />
          <TaskSection
            variant="overdue"
            title="Overdue"
            tasks={buckets.overdue}
            onView={(task) => setSelection({ task, mode: 'view' })}
            onEdit={(task) => setSelection({ task, mode: 'edit' })}
            onDelete={setConfirmingDelete}
            onToggleStatus={handleToggleStatus}
            paused={modalOpen}
          />
          <TaskSection
            variant="upcoming"
            title="Upcoming"
            tasks={buckets.upcoming}
            onView={(task) => setSelection({ task, mode: 'view' })}
            onEdit={(task) => setSelection({ task, mode: 'edit' })}
            onDelete={setConfirmingDelete}
            onToggleStatus={handleToggleStatus}
            paused={modalOpen}
          />
        </>
      )}

      <FloatingAddButton />
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
