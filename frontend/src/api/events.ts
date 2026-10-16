import { request } from './client'

export type EventStatus = 'SCHEDULED' | 'ACTIVE' | 'FINISHED' | 'CANCELLED'

/** Mirrors com.eventplatform.api.dto.event.EventResponse. Dates are LocalDateTime: "yyyy-MM-ddTHH:mm:ss". */
export interface Event {
  id: number
  name: string
  description: string | null
  startDate: string
  endDate: string
  capacity: number
  registeredCount: number
  status: EventStatus
}

/** Mirrors CreateEventRequest and UpdateEventRequest (PUT replaces every field). */
export interface EventRequest {
  name: string
  description: string | null
  startDate: string
  endDate: string
  capacity: number
}

/** Mirrors EventAvailabilityResponse */
export interface EventAvailability {
  eventId: number
  capacity: number
  registeredCount: number
  availableSeats: number
  waitlistCount: number
  status: EventStatus
  acceptingRegistrations: boolean
}

/** Valid transitions enforced by EventService (any other change returns 409). */
export const NEXT_STATUSES: Record<EventStatus, EventStatus[]> = {
  SCHEDULED: ['ACTIVE', 'CANCELLED'],
  ACTIVE: ['FINISHED', 'CANCELLED'],
  FINISHED: [],
  CANCELLED: [],
}

export const eventsApi = {
  list: () => request<Event[]>('GET', '/api/events'),
  get: (id: number) => request<Event>('GET', `/api/events/${id}`),
  create: (data: EventRequest) => request<Event>('POST', '/api/events', data),
  update: (id: number, data: EventRequest) => request<Event>('PUT', `/api/events/${id}`, data),
  updateStatus: (id: number, status: EventStatus) =>
    request<Event>('PATCH', `/api/events/${id}/status`, { status }),
  availability: (id: number) => request<EventAvailability>('GET', `/api/events/${id}/availability`),
}
