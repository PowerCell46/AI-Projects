import { format, isToday, isTomorrow, isYesterday, parseISO } from 'date-fns'

export function formatDueDate(iso: string | null): string {
  if (!iso) return 'No due date'
  const d = parseISO(iso)
  const time = format(d, 'h:mm a')
  if (isToday(d)) return `Today, ${time}`
  if (isTomorrow(d)) return `Tomorrow, ${time}`
  if (isYesterday(d)) return `Yesterday, ${time}`
  return format(d, 'MMM d, h:mm a')
}

export function toDateInputValue(iso: string | null): string {
  if (!iso) return ''
  const d = parseISO(iso)
  return format(d, "yyyy-MM-dd'T'HH:mm")
}
