import { apiClient, queryString } from './apiClient'
import type { Approval, Employee, PageResponse, AuditEvent } from '../types/domain'

export interface EmployeeQuery {
  page?: number
  size?: number
  search?: string
  department?: string
  role?: string
  accountStatus?: string
  riskLevel?: string
  sort?: string
}

export interface CreateEmployeePayload {
  personal: Record<string, unknown>
  employment: Record<string, unknown>
  account: Record<string, unknown>
  credential: {
    method: 'SET' | 'GENERATE'
    password?: string
    forceChange: boolean
  }
}

export const employeeApi = {
  list: (query: EmployeeQuery = {}) =>
    apiClient.get<PageResponse<Employee>>(`/employees${queryString(query)}`),

  get: (id: string) =>
    apiClient.get<Employee>(`/employees/${encodeURIComponent(id)}`),

  create: (payload: CreateEmployeePayload) =>
    apiClient.post<Approval>('/employees', payload),

  update: (id: string, payload: Partial<Employee>) =>
    apiClient.put<Approval>(`/employees/${encodeURIComponent(id)}`, payload),

  transition: (id: string, action: string, reason?: string) =>
    apiClient.post<Approval>(
      `/employees/${encodeURIComponent(id)}/account-actions`,
      { action, reason }
    ),

  resetPassword: (
    id: string,
    generateTemporary: boolean,
    forceChange: boolean
  ) =>
    apiClient.post<Approval>(
      `/employees/${encodeURIComponent(id)}/credentials/reset`,
      { generateTemporary, forceChange }
    ),

  auditHistory: (
    id: string,
    query: Pick<EmployeeQuery, 'page' | 'size' | 'sort'> = {}
  ) =>
    apiClient.get<PageResponse<AuditEvent>>(
      `/employees/${encodeURIComponent(id)}/audit-events${queryString(query)}`
    ),

  validateUnique: (field: 'employeeId' | 'email', value: string) =>
    apiClient.get<{ available: boolean }>(
      `/employees/validate${queryString({ field, value })}`
    ),
}
