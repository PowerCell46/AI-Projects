import { useState, type FormEvent, type MouseEvent } from 'react'
import { parseISO, format } from 'date-fns'
import Field from '../Field/Field'
import TextInput from '../TextInput/TextInput'
import TextArea from '../TextArea/TextArea'
import Select from '../Select/Select'
import DatePickerField from '../DatePickerField/DatePickerField.tsx'
import { useCategoriesQuery } from '../../hooks/useCategoriesQuery'
import { useUpdateTaskMutation } from '../../hooks/useUpdateTaskMutation'
import { useModalEffects } from '../../utils/useModalEffects'
import type { ApiPriority, ApiStatus, TaskResponse, TaskUpdateRequest } from '../../types'
import './EditTaskModal.css'

export type TaskModalMode = 'view' | 'edit'

interface EditTaskModalProps {
  task: TaskResponse | null
  open: boolean
  mode: TaskModalMode
  onClose: () => void
  onSwitchToEdit?: () => void
}

const PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'LOW' },
  { value: 'MEDIUM', label: 'MEDIUM' },
  { value: 'HIGH', label: 'HIGH' },
]

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'COMPLETED', label: 'Completed' },
]

const STATUS_LABEL: Record<ApiStatus, string> = {
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
}

export default function EditTaskModal({
  task,
  open,
  mode,
  onClose,
  onSwitchToEdit,
}: EditTaskModalProps) {
  if (!open || !task) return null
  return (
    <EditTaskModalInner
      key={`${task.id}:${mode}`}
      task={task}
      mode={mode}
      onClose={onClose}
      onSwitchToEdit={onSwitchToEdit}
    />
  )
}

interface InnerProps {
  task: TaskResponse
  mode: TaskModalMode
  onClose: () => void
  onSwitchToEdit?: () => void
}

interface FormState {
  title: string
  description: string
  dueDate: Date | null
  priority: ApiPriority
  status: ApiStatus
  categoryId: string
}

function EditTaskModalInner({ task, mode, onClose, onSwitchToEdit }: InnerProps) {
  const { dialogRef } = useModalEffects(true, onClose)
  const categoriesQuery = useCategoriesQuery()
  const updateMutation = useUpdateTaskMutation()
  const [submitAttempted, setSubmitAttempted] = useState(false)
  const [form, setForm] = useState<FormState>({
    title: task.title,
    description: task.description ?? '',
    dueDate: task.dueDate ? parseISO(task.dueDate) : null,
    priority: task.priority,
    status: task.status,
    categoryId: task.categoryId,
  })

  const categories = categoriesQuery.data?.content ?? []
  const trimmedTitle = form.title.trim()
  const submitDisabled = trimmedTitle.length === 0 || updateMutation.isPending
  const isView = mode === 'view'

  const handleBackdrop = (e: MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose()
  }

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setSubmitAttempted(true)
    if (isView || trimmedTitle.length === 0) return
    const patch: TaskUpdateRequest = {
      title: trimmedTitle,
      description: form.description.trim() || undefined,
      dueDate: form.dueDate
        ? format(form.dueDate, "yyyy-MM-dd'T'HH:mm:ss")
        : null,
      priority: form.priority,
      status: form.status,
      categoryId: form.categoryId,
    }
    updateMutation.mutate(
      { id: task.id, patch },
      { onSuccess: () => onClose() },
    )
  }

  const categoryOptions = categories.map((c) => ({ value: c.id, label: c.name }))
  const categoryName =
    categories.find((c) => c.id === task.categoryId)?.name ?? task.categoryName
  const dueDateDisplay = task.dueDate
    ? format(parseISO(task.dueDate), 'EEE, MMM d, yyyy · HH:mm')
    : 'No due date'
  const descriptionDisplay = task.description?.trim() ? task.description : '—'

  return (
    <div className="modal-backdrop" onClick={handleBackdrop} role="presentation">
      <div
        ref={dialogRef}
        className="edit-task-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-task-title"
      >
        <header className="edit-task-heading">
          <div>
            <h2 id="edit-task-title" className="edit-task-title">
              {isView ? 'Task details' : 'Edit task'}
            </h2>
            <p className="edit-task-subtitle">
              {isView ? 'Review the details for this task.' : 'Update the details for this task.'}
            </p>
          </div>
          <button
            type="button"
            className="edit-task-close"
            onClick={onClose}
            aria-label="Close"
          >
            ×
          </button>
        </header>

        <form className="edit-task-form" onSubmit={handleSubmit} noValidate>
          <div className="edit-task-fields">
            <Field label="Title">
              {isView ? (
                <p className="edit-task-readonly">{task.title}</p>
              ) : (
                <TextInput
                  value={form.title}
                  maxLength={200}
                  required
                  aria-required="true"
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') e.preventDefault()
                  }}
                />
              )}
            </Field>

            <Field label="Description">
              {isView ? (
                <p className="edit-task-readonly edit-task-readonly--multiline">
                  {descriptionDisplay}
                </p>
              ) : (
                <TextArea
                  value={form.description}
                  maxLength={2000}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              )}
            </Field>

            <Field label="Due date">
              {isView ? (
                <p className="edit-task-readonly">{dueDateDisplay}</p>
              ) : (
                <DatePickerField
                  value={form.dueDate}
                  onChange={(d) => setForm({ ...form, dueDate: d })}
                />
              )}
            </Field>

            <Field label="Priority">
              {isView ? (
                <p className="edit-task-readonly">{task.priority}</p>
              ) : (
                <Select
                  value={form.priority}
                  options={PRIORITY_OPTIONS}
                  ariaLabel="Priority"
                  onChange={(v) => setForm({ ...form, priority: v as ApiPriority })}
                />
              )}
            </Field>

            <Field label="Status">
              {isView ? (
                <p className="edit-task-readonly">{STATUS_LABEL[task.status]}</p>
              ) : (
                <Select
                  value={form.status}
                  options={STATUS_OPTIONS}
                  ariaLabel="Status"
                  onChange={(v) => setForm({ ...form, status: v as ApiStatus })}
                />
              )}
            </Field>

            <Field label="Category">
              {isView ? (
                <p className="edit-task-readonly">{categoryName}</p>
              ) : (
                <Select
                  value={form.categoryId}
                  options={categoryOptions}
                  placeholder={
                    categoriesQuery.isLoading ? 'Loading categories…' : 'No categories yet'
                  }
                  ariaLabel="Category"
                  onChange={(v) => setForm({ ...form, categoryId: v })}
                />
              )}
            </Field>
          </div>

          {!isView && submitAttempted && trimmedTitle.length === 0 && (
            <p className="edit-task-error" role="alert">
              Title is required.
            </p>
          )}
          {!isView && updateMutation.isError && (
            <p className="edit-task-error" role="alert">
              Couldn’t save the changes. Please try again.
            </p>
          )}

          <footer className="edit-task-footer">
            {isView ? (
              <>
                <button
                  type="button"
                  className="edit-task-button edit-task-button--discard"
                  onClick={onClose}
                >
                  Close
                </button>
                <button
                  type="button"
                  className="edit-task-button edit-task-button--submit"
                  onClick={onSwitchToEdit}
                  disabled={!onSwitchToEdit}
                >
                  Edit
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className="edit-task-button edit-task-button--discard"
                  onClick={onClose}
                  disabled={updateMutation.isPending}
                >
                  Discard
                </button>
                <button
                  type="submit"
                  className="edit-task-button edit-task-button--submit"
                  disabled={submitDisabled}
                >
                  {updateMutation.isPending ? 'Saving…' : 'Submit'}
                </button>
              </>
            )}
          </footer>
        </form>
      </div>
    </div>
  )
}
