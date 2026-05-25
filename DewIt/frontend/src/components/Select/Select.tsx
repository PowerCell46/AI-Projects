import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useOutsideClick } from '../../utils/useOutsideClick'
import './Select.css'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectFooterApi {
  close: () => void
}

interface SelectProps {
  value: string
  options: SelectOption[]
  placeholder?: string
  onChange: (value: string) => void
  ariaLabel?: string
  disabled?: boolean
  /** Optional content rendered at the bottom of the dropdown panel, separated by a divider. */
  footer?: (api: SelectFooterApi) => ReactNode
}

export default function Select({
  value,
  options,
  placeholder = 'Select…',
  onChange,
  ariaLabel,
  disabled = false,
  footer,
}: SelectProps) {
  const wrapperRef = useRef<HTMLDivElement | null>(null)
  const listboxId = useId()
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() =>
    Math.max(0, options.findIndex((o) => o.value === value)),
  )

  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(wrapperRef, open, close)

  // Keep activeIndex in sync with the current value when the dropdown opens
  useEffect(() => {
    if (!open) return
    setActiveIndex(Math.max(0, options.findIndex((o) => o.value === value)))
  }, [open, options, value])

  const selected = options.find((o) => o.value === value)

  const handleTriggerKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'Escape' && open) {
      e.preventDefault()
      setOpen(false)
      return
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      setOpen(true)
    }
  }

  const handleListKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
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
      if (opt) {
        onChange(opt.value)
        setOpen(false)
      }
    }
  }

  return (
    <div className="select" ref={wrapperRef}>
      <button
        type="button"
        className={`field-control select-trigger${open ? ' is-open' : ''}`}
        onClick={() => !disabled && setOpen((v) => !v)}
        onKeyDown={handleTriggerKeyDown}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listboxId : undefined}
        aria-label={ariaLabel}
        disabled={disabled}
      >
        <span className={`select-value${selected ? '' : ' is-placeholder'}`}>
          {selected ? selected.label : placeholder}
        </span>
        <span className={`select-chevron${open ? ' is-open' : ''}`} aria-hidden="true">▼</span>
      </button>
      {open && (
        <div className="select-panel">
          <div
            className="select-options"
            role="listbox"
            id={listboxId}
            tabIndex={-1}
            ref={(el) => el?.focus()}
            onKeyDown={handleListKeyDown}
          >
            {options.map((opt, idx) => {
              const isSelected = opt.value === value
              const isActive = idx === activeIndex
              return (
                <button
                  key={opt.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={`select-option${isSelected ? ' is-selected' : ''}${isActive ? ' is-active' : ''}`}
                  onClick={() => {
                    onChange(opt.value)
                    setOpen(false)
                  }}
                  onMouseEnter={() => setActiveIndex(idx)}
                >
                  {opt.label}
                </button>
              )
            })}
          </div>
          {footer && <div className="select-footer">{footer({ close })}</div>}
        </div>
      )}
    </div>
  )
}
