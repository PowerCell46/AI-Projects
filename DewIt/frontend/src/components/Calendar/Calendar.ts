export interface MonthGrid {
  cells: (number | null)[]
  monthLabel: string
  year: number
  month: number
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export function buildMonthGrid(year: number, month: number): MonthGrid {
  // Week starts on Monday: shift JS Sunday=0 → Monday=0.
  const firstDayOffset = (new Date(year, month, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: (number | null)[] = []
  for (let i = 0; i < firstDayOffset; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)
  while (cells.length % 7 !== 0) cells.push(null)
  return {
    cells,
    monthLabel: `${MONTH_NAMES[month]} ${year}`,
    year,
    month,
  }
}

export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  let m = month + delta
  let y = year
  while (m < 0) { m += 12; y -= 1 }
  while (m > 11) { m -= 12; y += 1 }
  return { year: y, month: m }
}

export function isSameDay(a: Date | null, b: Date | null): boolean {
  if (!a || !b) return false
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

const WEEKDAYS_FULL = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

export function fullDateLabel(year: number, month: number, day: number): string {
  const d = new Date(year, month, day)
  const weekday = WEEKDAYS_FULL[d.getDay()]
  return `${weekday}, ${MONTH_NAMES[month]} ${day}, ${year}`
}
