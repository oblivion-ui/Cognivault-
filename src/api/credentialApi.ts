import { apiClient } from './apiClient'
export const credentialApi = { changeOwn: (currentPassword: string, newPassword: string) => apiClient.post<void>('/credentials/change-password', { currentPassword, newPassword }), status: (employeeId: string) => apiClient.get<Record<string, unknown>>(`/employees/${employeeId}/credentials`) }
