import type { EventStatus } from '../api/events'
import type { RegistrationStatus } from '../api/registrations'
import type { UserStatus } from '../api/users'

export type AnyStatus = EventStatus | RegistrationStatus | UserStatus

export type StatusTone = 'scheduled' | 'active' | 'waitlist' | 'cancelled' | 'finished'

// The enum names stay as the backend sends them; only the UI shows these labels.
const STATUS_UI: Record<AnyStatus, { label: string; tone: StatusTone }> = {
  SCHEDULED: { label: 'Scheduled', tone: 'scheduled' },
  ACTIVE: { label: 'Active', tone: 'active' },
  FINISHED: { label: 'Finished', tone: 'finished' },
  CANCELLED: { label: 'Cancelled', tone: 'cancelled' },
  CONFIRMED: { label: 'Confirmed', tone: 'active' },
  WAITING_LIST: { label: 'Waitlisted', tone: 'waitlist' },
  INACTIVE: { label: 'Inactive', tone: 'finished' },
}

export function statusLabel(status: AnyStatus): string {
  return STATUS_UI[status]?.label ?? status
}

export function statusTone(status: AnyStatus): StatusTone {
  return STATUS_UI[status]?.tone ?? 'finished'
}
