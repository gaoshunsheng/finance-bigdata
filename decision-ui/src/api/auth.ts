import { get, post } from './request'
import type { LoginRequest, LoginResponse, User } from '@/types'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return post('/auth/login', data)
}

export function refreshToken(refreshToken: string): Promise<{ accessToken: string; refreshToken: string }> {
  return post('/auth/refresh', { refreshToken })
}

export function logout(): Promise<void> {
  return post('/auth/logout')
}

export function getCurrentUser(): Promise<User> {
  return get('/auth/me')
}

export function createUser(data: {
  username: string
  password: string
  displayName: string
  email: string
  role: string
}): Promise<User> {
  return post('/auth/users', data)
}

export function listUsers(): Promise<User[]> {
  return get('/auth/users')
}

export function changePassword(data: {
  oldPassword: string
  newPassword: string
}): Promise<void> {
  return post('/auth/change-password', data)
}
