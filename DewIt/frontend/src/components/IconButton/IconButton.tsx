import type { MouseEvent } from 'react'
import './IconButton.css'

type Variant = 'edit' | 'delete'

interface IconButtonProps {
  variant: Variant
  onClick: (e: MouseEvent<HTMLButtonElement>) => void
  ariaLabel: string
}

export default function IconButton({ variant, onClick, ariaLabel }: IconButtonProps) {
  return (
    <button
      type="button"
      className={`icon-button icon-button--${variant}`}
      aria-label={ariaLabel}
      onClick={(e) => {
        e.stopPropagation()
        onClick(e)
      }}
    >
      {variant === 'edit' ? <PencilIcon /> : <TrashIcon />}
    </button>
  )
}

function PencilIcon() {
  return (
    <svg
      width="12"
      height="12"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M11.5 2.5l2 2L5 13H3v-2L11.5 2.5z" />
      <path d="M10.5 3.5l2 2" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg
      width="12"
      height="12"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 4.5h10" />
      <path d="M6.5 4.5V3a1 1 0 011-1h1a1 1 0 011 1v1.5" />
      <path d="M4.5 4.5l.5 8a1 1 0 001 1h4a1 1 0 001-1l.5-8" />
      <path d="M7 7v4M9 7v4" />
    </svg>
  )
}
