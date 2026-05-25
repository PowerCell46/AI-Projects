import Carousel from '../Carousel/Carousel.tsx'
import NavButtons from '../NavButtons/NavButtons'
import TaskCard from '../TaskCard/TaskCard'
import SkeletonCard from '../SkeletonCard/SkeletonCard'
import { useVisibleSlots } from '../../hooks/useVisibleSlots'
import type { TaskResponse } from '../../types'
import './TaskSection.css'

export type SectionVariant = 'today' | 'overdue' | 'upcoming'

interface TaskSectionProps {
  variant: SectionVariant
  title: string
  tasks: TaskResponse[]
  onView: (task: TaskResponse) => void
  onEdit: (task: TaskResponse) => void
  onDelete: (task: TaskResponse) => void
  onToggleStatus: (task: TaskResponse) => void
  paused?: boolean
}

export default function TaskSection({
  variant,
  title,
  tasks,
  onView,
  onEdit,
  onDelete,
  onToggleStatus,
  paused,
}: TaskSectionProps) {
  const visibleSlots = useVisibleSlots()
  const enableLoop = tasks.length > visibleSlots
  const showNav = enableLoop
  const skeletonCount = tasks.length < visibleSlots ? visibleSlots - tasks.length : 0

  return (
    <div className={`task-section task-section--${variant}`}>
      <Carousel
        intervalMs={5000}
        enableLoop={enableLoop}
        paused={paused}
        ariaLabel={`${title} tasks`}
        trackVariant="carousel-track--tasks"
        renderHeader={({ next, prev }) => (
          <header className="task-section-header carousel-header">
            <div className="task-section-heading">
              <h2 className="task-section-title">{title}</h2>
              <span className="task-section-subtitle">· {tasks.length} tasks</span>
            </div>
            {showNav && (
              <NavButtons onPrev={prev} onNext={next} labelContext={`${title} tasks`} />
            )}
          </header>
        )}
      >
        {tasks.map((task) => (
          <TaskCard
            key={task.id}
            task={task}
            onView={onView}
            onEdit={onEdit}
            onDelete={onDelete}
            onToggleStatus={onToggleStatus}
          />
        ))}
        {Array.from({ length: skeletonCount }).map((_, i) => (
          <SkeletonCard key={`skeleton-${i}`} />
        ))}
      </Carousel>
    </div>
  )
}
