// The backend uses LocalDateTime ("2030-05-10T18:00:00", no time zone),
// while <input type="datetime-local"> works with "2030-05-10T18:00".

export function toInputDateTime(apiValue: string): string {
  return apiValue.slice(0, 16)
}

export function toApiDateTime(inputValue: string): string {
  return inputValue.length === 16 ? `${inputValue}:00` : inputValue
}

const formatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export function formatDateTime(apiValue: string): string {
  const date = new Date(apiValue)
  return Number.isNaN(date.getTime()) ? apiValue : formatter.format(date)
}
