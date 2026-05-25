import { Link } from 'react-router-dom'
import './FloatingAddButton.css'

export default function FloatingAddButton() {
  return (
    <Link to="/tasks/new" className="floating-add" aria-label="Create new task">
      <span className="floating-add-plus" aria-hidden="true">+</span>
    </Link>
  )
}
