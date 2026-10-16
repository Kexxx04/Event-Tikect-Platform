import { request } from './client'
import type { EventStatus } from './events'

export type RegistrationStatus = 'CONFIRMED' | 'WAITING_LIST' | 'CANCELLED'

/** Mirrors com.eventplatform.api.dto.registration.RegistrationResponse */
export interface Registration {
  id: number
  eventId: number
  eventName: string
  userId: number
  userName: string
  userEmail: string
  registrationDate: string
  status: RegistrationStatus
}

/** Mirrors RegistrationCancellationResponse: promoted is null when nobody took the seat. */
export interface RegistrationCancellation {
  cancelled: Registration
  promoted: Registration | null
}

/** Mirrors UserEventResponse */
export interface UserEvent {
  registrationId: number
  eventId: number
  eventName: string
  startDate: string
  endDate: string
  eventStatus: EventStatus
  registrationDate: string
  registrationStatus: RegistrationStatus
}

export const registrationsApi = {
  /** Registrations of an event, oldest first (the order the waitlist is served in). */
  listByEvent: (eventId: number) => request<Registration[]>('GET', `/api/events/${eventId}/registrations`),
  register: (eventId: number, userId: number) =>
    request<Registration>('POST', `/api/events/${eventId}/registrations`, { userId }),
  cancel: (eventId: number, userId: number) =>
    request<RegistrationCancellation>('DELETE', `/api/events/${eventId}/registrations/${userId}`),
  eventsOfUser: (userId: number) => request<UserEvent[]>('GET', `/api/users/${userId}/events`),
}
