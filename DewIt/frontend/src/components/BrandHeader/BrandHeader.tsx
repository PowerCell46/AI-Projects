import { Link } from 'react-router-dom'
import './BrandHeader.css'

export default function BrandHeader() {
  return (
    <header className="brand-header">
      <h1 className="brand-header-title">DewIt</h1>
      <Link to="/stats" className="brand-header-stats">
        <svg
          width="15"
          height="15"
          viewBox="0 0 15 15"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <path
            d="M2 13V8.5M7.5 13V5M13 13V2"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
          />
        </svg>
        <span className="brand-header-stats-label">Stats</span>
      </Link>
    </header>
  )
}
