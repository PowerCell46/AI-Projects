import type { MouseEvent } from 'react'
import './Checkbox.css'

interface CheckboxProps {
  checked: boolean
  onToggle: () => void
  ariaLabel?: string
}

export default function Checkbox({ checked, onToggle, ariaLabel }: CheckboxProps) {
  const label = ariaLabel ?? (checked ? 'Mark as incomplete' : 'Mark as complete')

  const handleClick = (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation()
    onToggle()
  }

  return (
    <button
      type="button"
      className={`checkbox${checked ? ' checkbox--checked' : ''}`}
      aria-label={label}
      aria-pressed={checked}
      onClick={handleClick}
    >
      {checked && (
        <svg
          className="checkbox-icon"
          viewBox="0 0 10 10"
          width="10"
          height="10"
          aria-hidden="true"
          focusable="false"
        >
          <path
            d="M1.5 5.2L4 7.5L8.5 2.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}
    </button>
  )
}
