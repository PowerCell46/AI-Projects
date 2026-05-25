import type { KeyboardEvent } from 'react'
import Checkbox from '../Checkbox/Checkbox'
import IconButton from '../IconButton/IconButton'
import { formatDueDate } from '../../utils/formatDueDate'
import type { TaskResponse } from '../../types'
import './TaskCard.css'

interface TaskCardProps {
  task: TaskResponse
  onView: (task: TaskResponse) => void
  onEdit: (task: TaskResponse) => void
  onDelete: (task: TaskResponse) => void
  onToggleStatus: (task: TaskResponse) => void
}

export default function TaskCard({
  task,
  onView,
  onEdit,
  onDelete,
  onToggleStatus,
}: TaskCardProps) {
  const completed = task.status === 'COMPLETED'

  const handleKey = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.target !== e.currentTarget) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      onView(task)
    }
  }

  return (
    <div
      className={`task-card${completed ? ' task-card--completed' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => onView(task)}
      onKeyDown={handleKey}
      aria-label={`View task: ${task.title}`}
    >
      <div className="task-card-top">
        <div className="task-card-top-left">
          <Checkbox checked={completed} onToggle={() => onToggleStatus(task)} />
          <span className="task-card-category">{task.categoryName}</span>
        </div>
        <span className="task-card-priority">{task.priority}</span>
      </div>
      <h3 className="task-card-title">{task.title}</h3>
      {task.description && <p className="task-card-description">{task.description}</p>}
      <div className="task-card-footer">
        <span className="task-card-due">{formatDueDate(task.dueDate)}</span>
        <div className="task-card-actions">
          <IconButton
            variant="edit"
            ariaLabel={`Edit ${task.title}`}
            onClick={() => onEdit(task)}
          />
          <IconButton
            variant="delete"
            ariaLabel={`Delete ${task.title}`}
            onClick={() => onDelete(task)}
          />
        </div>
      </div>
    </div>
  )
}
