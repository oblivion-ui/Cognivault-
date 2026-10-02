import { apiClient, queryString } from './apiClient'
import type { AuditEvent, PageResponse } from '../types/domain'
export const auditApi = {
  list: (filters: Record<string, unknown> = {}) => apiClient.get<PageResponse<AuditEvent>>(`/audit-events${queryString(filters)}`),
  get: (id: string) => apiClient.get<AuditEvent>(`/audit-events/${encodeURIComponent(id)}`),
  export: (filters: Record<string, unknown>) => apiClient.post<{ downloadUrl: string }>('/audit-events/exports', filters),
}
