import { useCallback, useEffect, useRef, useState } from 'react'
import Calendar from '../Calendar/Calendar.tsx'
import { useOutsideClick } from '../../utils/useOutsideClick'
import { format } from 'date-fns'
import './DatePickerField.css'

interface DatePickerFieldProps {
  value: Date | null
  onChange: (value: Date | null) => void
  placeholder?: string
}

const DEFAULT_TIME = '09:00'

function timeStringFor(value: Date | null): string {
  return value ? format(value, 'HH:mm') : DEFAULT_TIME
}

function combine(day: Date, time: string): Date {
  const [hhRaw, mmRaw] = time.split(':')
  const hh = Number(hhRaw)
  const mm = Number(mmRaw)
  const safeHh = Number.isFinite(hh) ? hh : 9
  const safeMm = Number.isFinite(mm) ? mm : 0
  return new Date(
    day.getFullYear(),
    day.getMonth(),
    day.getDate(),
    safeHh,
    safeMm,
    0,
  )
}

export default function DatePickerField({
  value,
  onChange,
  placeholder = 'Select a date',
}: DatePickerFieldProps) {
  const wrapperRef = useRef<HTMLDivElement | null>(null)
  const [open, setOpen] = useState(false)
  const [time, setTime] = useState<string>(() => timeStringFor(value))

  // Keep local time in sync when the parent's value changes (e.g. modal opens with a saved task).
  useEffect(() => {
    setTime(timeStringFor(value))
  }, [value])

  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(wrapperRef, open, close)

  const onKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'Escape' && open) {
      e.preventDefault()
      setOpen(false)
    }
  }

  const handleDaySelect = (day: Date) => {
    onChange(combine(day, time))
    setOpen(false)
  }

  const handleTimeChange = (next: string) => {
    setTime(next)
    if (value) onChange(combine(value, next))
  }

  const display = value
    ? `${format(value, 'EEE, MMM d, yyyy')} · ${format(value, 'HH:mm')}`
    : placeholder

  return (
    <div className="date-picker" ref={wrapperRef}>
      <button
        type="button"
        className={`field-control date-picker-trigger${open ? ' is-open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        onKeyDown={onKeyDown}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className={`date-picker-value${value ? '' : ' is-placeholder'}`}>{display}</span>
        <span className="date-picker-icon" aria-hidden="true">◌</span>
      </button>
      {open && (
        <div className="date-picker-popover" role="dialog" aria-label="Choose a date and time">
          <Calendar
            value={value}
            onSelect={handleDaySelect}
            onClear={() => onChange(null)}
          />
          <div className="date-picker-time-row">
            <label className="date-picker-time-label" htmlFor="date-picker-time-input">
              Time
            </label>
            <input
              id="date-picker-time-input"
              type="time"
              className="date-picker-time-input"
              value={time}
              onChange={(e) => handleTimeChange(e.target.value)}
            />
          </div>
        </div>
      )}
    </div>
  )
}
