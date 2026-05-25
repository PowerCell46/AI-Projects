import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useOutsideClick } from '../../utils/useOutsideClick'
import './FilterSelect.css'

export interface FilterSelectOption<T extends string> {
  value: T
  label: string
}

interface FilterSelectProps<T extends string> {
  label: string
  value: T
  options: FilterSelectOption<T>[]
  onChange: (value: T) => void
}

export default function FilterSelect<T extends string>({
  label,
  value,
  options,
  onChange,
}: FilterSelectProps<T>) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() =>
    Math.max(0, options.findIndex((o) => o.value === value)),
  )
  const wrapperRef = useRef<HTMLDivElement | null>(null)
  const listboxId = useId()
  const current = options.find((o) => o.value === value) ?? options[0]

  useOutsideClick(wrapperRef, open, () => setOpen(false))

  useEffect(() => {
    if (!open) return
    setActiveIndex(Math.max(0, options.findIndex((o) => o.value === value)))
  }, [open, options, value])

  const choose = (next: T) => {
    onChange(next)
    setOpen(false)
  }

  const handleTriggerKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      setOpen(true)
    }
  }

  const handleListKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      setOpen(false)
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex((i) => (i + 1) % options.length)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => (i - 1 + options.length) % options.length)
      return
    }
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      const opt = options[activeIndex]
      if (opt) choose(opt.value)
    }
  }

  return (
    <div className="filter-select" ref={wrapperRef}>
      <button
        type="button"
        className={`filter-select-trigger${open ? ' filter-select-trigger--open' : ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listboxId : undefined}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={handleTriggerKeyDown}
      >
        <span className="filter-select-label">{label}:</span>
        <span className="filter-select-value">{current?.label}</span>
        <span
          className={`filter-select-chevron${open ? ' filter-select-chevron--open' : ''}`}
          aria-hidden="true"
        >
          ▼
        </span>
      </button>

      {open && (
        <div
          className="filter-select-panel"
          role="listbox"
          id={listboxId}
          tabIndex={-1}
          ref={(el) => el?.focus()}
          onKeyDown={handleListKeyDown}
        >
          {options.map((opt, idx) => {
            const selected = opt.value === value
            const active = idx === activeIndex
            return (
              <button
                type="button"
                key={opt.value}
                role="option"
                aria-selected={selected}
                className={`filter-select-option${
                  selected ? ' filter-select-option--selected' : ''
                }${active ? ' filter-select-option--active' : ''}`}
                onClick={() => choose(opt.value)}
                onMouseEnter={() => setActiveIndex(idx)}
              >
                {opt.label}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
