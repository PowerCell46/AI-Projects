import { Link } from 'react-router-dom'
import './CategoryCard.css'

interface CategoryCardProps {
  id: string
  name: string
  taskCount: number
}

export default function CategoryCard({ id, name, taskCount }: CategoryCardProps) {
  return (
    <Link to={`/categories/${id}`} className="category-card">
      <span className="category-card-name">{name}</span>
      <span className="category-card-count">
        {taskCount} {taskCount === 1 ? 'TASK' : 'TASKS'}
      </span>
    </Link>
  )
}
