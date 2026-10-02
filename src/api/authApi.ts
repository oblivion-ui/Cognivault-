import { apiClient } from './apiClient'
import type { User } from '../types/domain'
export interface LoginResponse { accessToken: string; refreshToken?: string; expiresIn?: number; user: User }
export const authApi = {
  login: (username: string, password: string) => apiClient.post<LoginResponse>('/auth/login', { username, password }, { skipAuth: true }),
  me: () => apiClient.get<User>('/auth/me'),
  changePassword: (data: { currentPassword: string; newPassword: string }) => apiClient.post<{ reauthenticationRequired?: boolean }>('/auth/change-password', data),
  logout: () => apiClient.post<void>('/auth/logout'),
}
