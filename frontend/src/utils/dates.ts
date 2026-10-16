// The backend uses LocalDateTime ("2030-05-10T18:00:00", no time zone),
// while <input type="datetime-local"> works with "2030-05-10T18:00".

export function toInputDateTime(apiValue: string): string {
  return apiValue.slice(0, 16)
}

export function toApiDateTime(inputValue: string): string {
  return inputValue.length === 16 ? `${inputValue}:00` : inputValue
}

const formatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })
const dateFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' })
const timeFormatter = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' })
const monthDayFormatter = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' })
const monthFormatter = new Intl.DateTimeFormat('en-US', { month: 'short' })

function parse(apiValue: string): Date | null {
  const date = new Date(apiValue)
  return Number.isNaN(date.getTime()) ? null : date
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

/** "Nov 20, 2026, 9:00 AM" */
export function formatDateTime(apiValue: string): string {
  const date = parse(apiValue)
  return date ? formatter.format(date) : apiValue
}

/** "Nov 20, 2026" */
export function formatDate(apiValue: string): string {
  const date = parse(apiValue)
  return date ? dateFormatter.format(date) : apiValue
}

/** Event card line: "Nov 28, 2026 · 6:00 PM" for a one-day event, "Nov 20 — 21, 2026" for a longer one. */
export function formatEventDates(startValue: string, endValue: string): string {
  const start = parse(startValue)
  const end = parse(endValue)
  if (!start || !end) return `${startValue} — ${endValue}`
  if (isSameDay(start, end)) return `${dateFormatter.format(start)} · ${timeFormatter.format(start)}`
  if (start.getFullYear() !== end.getFullYear()) return `${dateFormatter.format(start)} — ${dateFormatter.format(end)}`
  if (start.getMonth() === end.getMonth()) {
    return `${monthDayFormatter.format(start)} — ${end.getDate()}, ${end.getFullYear()}`
  }
  return `${monthDayFormatter.format(start)} — ${monthDayFormatter.format(end)}, ${end.getFullYear()}`
}

/** Detail header: "Nov 20, 9:00 AM — Nov 21, 6:00 PM"; the year is shown when it is not the current one. */
export function formatDateTimeRange(startValue: string, endValue: string): string {
  const start = parse(startValue)
  const end = parse(endValue)
  if (!start || !end) return `${startValue} — ${endValue}`
  const currentYear = new Date().getFullYear()
  const withYear = start.getFullYear() !== currentYear || end.getFullYear() !== currentYear
  const dayTime = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: withYear ? 'numeric' : undefined,
    hour: 'numeric',
    minute: '2-digit',
  })
  const endText = isSameDay(start, end) ? timeFormatter.format(end) : dayTime.format(end)
  return `${dayTime.format(start)} — ${endText}`
}

/** Month and day for a date tile: { month: "NOV", day: "05" }. */
export function dateTileParts(apiValue: string): { month: string; day: string } {
  const date = parse(apiValue)
  if (!date) return { month: '', day: '' }
  return { month: monthFormatter.format(date).toUpperCase(), day: String(date.getDate()).padStart(2, '0') }
}
