import { useId, useRef, type KeyboardEvent } from 'react'
import './SegmentedToggle.css'

export interface SegmentedToggleOption<T extends string> {
  value: T
  label: string
}

interface SegmentedToggleProps<T extends string> {
  value: T
  options: SegmentedToggleOption<T>[]
  onChange: (value: T) => void
  ariaLabel?: string
}

export default function SegmentedToggle<T extends string>({
  value,
  options,
  onChange,
  ariaLabel,
}: SegmentedToggleProps<T>) {
  const baseId = useId()
  const buttonsRef = useRef<(HTMLButtonElement | null)[]>([])

  const focusAt = (idx: number) => {
    const wrapped = (idx + options.length) % options.length
    const next = options[wrapped]
    if (!next) return
    buttonsRef.current[wrapped]?.focus()
    onChange(next.value)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>, idx: number) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault()
      focusAt(idx + 1)
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault()
      focusAt(idx - 1)
    } else if (e.key === 'Home') {
      e.preventDefault()
      focusAt(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      focusAt(options.length - 1)
    }
  }

  return (
    <div className="segmented-toggle" role="tablist" aria-label={ariaLabel}>
      {options.map((opt, idx) => {
        const active = opt.value === value
        return (
          <button
            key={opt.value}
            ref={(el) => {
              buttonsRef.current[idx] = el
            }}
            id={`${baseId}-${opt.value}`}
            type="button"
            role="tab"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            className={`segmented-toggle-segment${
              active ? ' segmented-toggle-segment--active' : ''
            }`}
            onClick={() => onChange(opt.value)}
            onKeyDown={(e) => onKeyDown(e, idx)}
          >
            {opt.label}
          </button>
        )
      })}
    </div>
  )
}
