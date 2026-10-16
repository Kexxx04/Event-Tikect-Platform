import { request } from './client'

export type UserStatus = 'ACTIVE' | 'INACTIVE'

/** Mirrors com.eventplatform.api.dto.user.UserResponse */
export interface User {
  id: number
  name: string
  email: string
  document: string
  status: UserStatus
}

/** Mirrors CreateUserRequest */
export interface CreateUserRequest {
  name: string
  email: string
  document: string
}

/** Mirrors UpdateUserRequest (PATCH: omitted fields keep their value) */
export interface UpdateUserRequest {
  name?: string
  email?: string
  document?: string
}

export const usersApi = {
  list: () => request<User[]>('GET', '/api/users'),
  get: (id: number) => request<User>('GET', `/api/users/${id}`),
  create: (data: CreateUserRequest) => request<User>('POST', '/api/users', data),
  update: (id: number, data: UpdateUserRequest) => request<User>('PATCH', `/api/users/${id}`, data),
  updateStatus: (id: number, status: UserStatus) =>
    request<User>('PATCH', `/api/users/${id}/status`, { status }),
}
