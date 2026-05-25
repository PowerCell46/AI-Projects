import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useCreateCategoryMutation } from '../../hooks/useCreateCategoryMutation'
import type { CategoryResponse } from '../../types'
import './AddCategoryInline.css'

interface AddCategoryInlineProps {
  onCreated: (category: CategoryResponse) => void
}

export default function AddCategoryInline({ onCreated }: AddCategoryInlineProps) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const inputRef = useRef<HTMLInputElement | null>(null)
  const mutation = useCreateCategoryMutation()

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  const reset = () => {
    setEditing(false)
    setName('')
    mutation.reset()
  }

  const submit = () => {
    const trimmed = name.trim()
    if (!trimmed) return
    mutation.mutate(
      { name: trimmed },
      {
        onSuccess: (created) => {
          onCreated(created)
          reset()
        },
      },
    )
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      submit()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      reset()
    }
  }

  if (!editing) {
    return (
      <button
        type="button"
        className="add-category-toggle"
        onClick={() => setEditing(true)}
      >
        <span className="add-category-plus" aria-hidden="true">+</span>
        <span>Add new category</span>
      </button>
    )
  }

  return (
    <div className="add-category-form">
      <input
        ref={inputRef}
        type="text"
        className="add-category-input"
        value={name}
        maxLength={100}
        placeholder="Category name"
        aria-label="New category name"
        onChange={(e) => setName(e.target.value)}
        onKeyDown={onKeyDown}
      />
      <div className="add-category-actions">
        <button
          type="button"
          className="add-category-button add-category-button--cancel"
          onClick={reset}
          disabled={mutation.isPending}
        >
          Cancel
        </button>
        <button
          type="button"
          className="add-category-button add-category-button--submit"
          onClick={submit}
          disabled={!name.trim() || mutation.isPending}
        >
          {mutation.isPending ? 'Adding…' : 'Add'}
        </button>
      </div>
      {mutation.isError && (
        <p className="add-category-error" role="alert">
          Couldn’t add the category. Try a different name.
        </p>
      )}
    </div>
  )
}
