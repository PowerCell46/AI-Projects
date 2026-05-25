import { useNavigate } from 'react-router-dom'
import './PageHeader.css'

interface PageHeaderProps {
  backLabel?: string
}

export default function PageHeader({ backLabel = 'Back' }: PageHeaderProps) {
  const navigate = useNavigate()
  return (
    <header className="page-header">
      <button
        type="button"
        className="page-header-back"
        onClick={() => navigate('/')}
      >
        <span className="page-header-back-arrow" aria-hidden="true">&larr;</span>
        <span>{backLabel}</span>
      </button>
      <span className="page-header-brand">DewIt</span>
    </header>
  )
}
