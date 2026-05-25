import { useMemo, useState, type FormEvent } from 'react'
import { isBefore } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader/PageHeader'
import Field from '../../components/Field/Field'
import TextInput from '../../components/TextInput/TextInput'
import TextArea from '../../components/TextArea/TextArea'
import Select from '../../components/Select/Select'
import DatePickerField from '../../components/DatePickerField/DatePickerField.tsx'
import AddCategoryInline from '../../components/AddCategoryInline/AddCategoryInline'
import { useCategoriesQuery } from '../../hooks/useCategoriesQuery'
import { useCreateTaskMutation } from '../../hooks/useCreateTaskMutation'
import type { ApiPriority, TaskCreateRequest } from '../../types'
import { format } from 'date-fns'
import './NewTaskView.css'

interface FormState {
  title: string
  description: string
  dueDate: Date | null
  priority: ApiPriority
  categoryId: string
}

const PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'LOW' },
  { value: 'MEDIUM', label: 'MEDIUM' },
  { value: 'HIGH', label: 'HIGH' },
]

const initialForm: FormState = {
  title: '',
  description: '',
  dueDate: null,
  priority: 'MEDIUM',
  categoryId: '',
}

export default function NewTaskView() {
  const navigate = useNavigate()
  const categoriesQuery = useCategoriesQuery()
  const createMutation = useCreateTaskMutation()
  const [form, setForm] = useState<FormState>(initialForm)
  const [submitAttempted, setSubmitAttempted] = useState(false)

  const categories = useMemo(
    () => categoriesQuery.data?.content ?? [],
    [categoriesQuery.data],
  )

  // Lazily fill the default category once the list arrives
  const effectiveCategoryId = useMemo(() => {
    if (form.categoryId) return form.categoryId
    return categories[0]?.id ?? ''
  }, [form.categoryId, categories])

  const trimmedTitle = form.title.trim()
  const dueDateInPast = form.dueDate != null && isBefore(form.dueDate, new Date())
  const submitDisabled =
    trimmedTitle.length === 0 || !effectiveCategoryId || dueDateInPast || createMutation.isPending

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setSubmitAttempted(true)
    if (trimmedTitle.length === 0 || !effectiveCategoryId) return

    const payload: TaskCreateRequest = {
      title: trimmedTitle,
      description: form.description.trim() || undefined,
      dueDate: form.dueDate
        ? format(form.dueDate, "yyyy-MM-dd'T'HH:mm:ss")
        : null,
      priority: form.priority,
      status: 'ACTIVE',
      categoryId: effectiveCategoryId,
    }

    createMutation.mutate(payload, { onSuccess: () => navigate('/') })
  }

  const categoryOptions = categories.map((c) => ({ value: c.id, label: c.name }))
  const titleInvalid = submitAttempted && trimmedTitle.length === 0

  return (
    <div className="new-task-view">
      <PageHeader />

      <form className="new-task-card" onSubmit={handleSubmit} noValidate>
        <div className="new-task-heading">
          <h1 className="new-task-title">New task</h1>
          <p className="new-task-subtitle">Fill in the details below to add a task.</p>
        </div>

        <div className="new-task-fields">
          <Field label="Title">
            <TextInput
              value={form.title}
              maxLength={200}
              placeholder="e.g. Write quarterly report"
              required
              aria-required="true"
              aria-invalid={titleInvalid || undefined}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              onKeyDown={(e) => {
                if (e.key === 'Enter') e.preventDefault()
              }}
            />
          </Field>

          <Field label="Description">
            <TextArea
              value={form.description}
              maxLength={2000}
              placeholder="Add a short note (optional)"
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </Field>

          <Field label="Due date">
            <DatePickerField
              value={form.dueDate}
              onChange={(d) => setForm({ ...form, dueDate: d })}
            />
            {dueDateInPast && (
              <p className="new-task-field-error" role="alert">
                Due date cannot be in the past.
              </p>
            )}
          </Field>

          <Field label="Priority">
            <Select
              value={form.priority}
              options={PRIORITY_OPTIONS}
              ariaLabel="Priority"
              onChange={(v) => setForm({ ...form, priority: v as ApiPriority })}
            />
          </Field>

          <Field label="Category">
            <Select
              value={effectiveCategoryId}
              options={categoryOptions}
              placeholder={
                categoriesQuery.isLoading ? 'Loading categories…' : 'No categories yet'
              }
              ariaLabel="Category"
              disabled={categoriesQuery.isLoading}
              onChange={(v) => setForm({ ...form, categoryId: v })}
              footer={({ close }) => (
                <AddCategoryInline
                  onCreated={(c) => {
                    setForm((f) => ({ ...f, categoryId: c.id }))
                    close()
                  }}
                />
              )}
            />
          </Field>
        </div>

        {createMutation.isError && (
          <p className="new-task-error" role="alert">
            Couldn’t create the task. Please try again.
          </p>
        )}

        <footer className="new-task-footer">
          <button
            type="submit"
            className="new-task-submit"
            disabled={submitDisabled}
          >
            {createMutation.isPending ? 'Creating…' : 'Create task'}
          </button>
        </footer>
      </form>
    </div>
  )
}
