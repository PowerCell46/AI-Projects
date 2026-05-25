import { useState } from 'react'
import {
  buildMonthGrid,
  fullDateLabel,
  isSameDay,
  shiftMonth,
} from './Calendar'
import './Calendar.css'

interface CalendarProps {
  value: Date | null
  onSelect: (date: Date) => void
  onClear: () => void
}

const DAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

export default function Calendar({ value, onSelect, onClear }: CalendarProps) {
  const today = new Date()
  const initial = value ?? today
  const [{ year, month }, setView] = useState({
    year: initial.getFullYear(),
    month: initial.getMonth(),
  })

  const grid = buildMonthGrid(year, month)

  const goPrev = () => setView(shiftMonth(year, month, -1))
  const goNext = () => setView(shiftMonth(year, month, 1))

  const selectToday = () => {
    onSelect(new Date(today.getFullYear(), today.getMonth(), today.getDate()))
  }

  return (
    <div className="calendar">
      <div className="calendar-nav">
        <button
          type="button"
          className="calendar-nav-button"
          onClick={goPrev}
          aria-label="Previous month"
        >
          <span aria-hidden="true">&larr;</span>
        </button>
        <div className="calendar-month-label" aria-live="polite">{grid.monthLabel}</div>
        <button
          type="button"
          className="calendar-nav-button"
          onClick={goNext}
          aria-label="Next month"
        >
          <span aria-hidden="true">&rarr;</span>
        </button>
      </div>

      <div className="calendar-weekdays" aria-hidden="true">
        {DAY_LABELS.map((d, i) => (
          <div key={i} className="calendar-weekday">{d}</div>
        ))}
      </div>

      <div className="calendar-grid" role="grid">
        {grid.cells.map((day, i) => {
          if (day === null) return <div key={i} className="calendar-cell calendar-cell--empty" />
          const cellDate = new Date(year, month, day)
          const isToday = isSameDay(cellDate, today)
          const isSelected = isSameDay(cellDate, value)
          const classes = [
            'calendar-cell',
            'calendar-day',
            isToday ? 'is-today' : '',
            isSelected ? 'is-selected' : '',
          ].filter(Boolean).join(' ')
          return (
            <button
              key={i}
              type="button"
              className={classes}
              aria-label={fullDateLabel(year, month, day)}
              aria-pressed={isSelected}
              onClick={() => onSelect(cellDate)}
            >
              {day}
            </button>
          )
        })}
      </div>

      <div className="calendar-footer">
        <button type="button" className="calendar-shortcut" onClick={selectToday}>
          Today
        </button>
        {value && (
          <button
            type="button"
            className="calendar-shortcut calendar-shortcut--clear"
            onClick={onClear}
          >
            Clear
          </button>
        )}
      </div>
    </div>
  )
}
