import { apiClient, queryString } from './apiClient'
import type { Anomaly, Approval, DashboardSummary, IntegrityResult, Investigation, LifecycleRecord, LineageNode, LineageRelationship, Notification, PageResponse, Prediction, Report, ServiceHealth, User } from '../types/domain'
export const dashboardApi = { summary: () => apiClient.get<DashboardSummary>('/dashboard/summary') }
export const approvalApi = { list: (q = {}) => apiClient.get<PageResponse<Approval>>(`/approvals${queryString(q)}`), get: (id: string) => apiClient.get<Approval>(`/approvals/${id}`), decide: (id: string, decision: 'APPROVE' | 'REJECT', comment: string) => apiClient.post<Approval>(`/approvals/${id}/decisions`, { decision, comment }) }
export const anomalyApi = { list: (q = {}) => apiClient.get<PageResponse<Anomaly>>(`/anomalies${queryString(q)}`), summary: () => apiClient.get<Record<string, unknown>>('/anomalies/summary') }
export const integrityApi = { latest: () => apiClient.get<IntegrityResult>('/integrity/latest'), verify: () => apiClient.post<IntegrityResult>('/integrity/verifications') }
export const investigatorApi = { investigate: (query: string) => apiClient.post<Investigation>('/investigations', { query }, { timeout: 240000 }), history: () => apiClient.get<Investigation[]>('/investigations') }
export const notificationApi = { list: () => apiClient.get<PageResponse<Notification>>('/notifications'), markRead: (id: string) => apiClient.patch<Notification>(`/notifications/${id}`, { read: true }), markAllRead: () => apiClient.post<void>('/notifications/read-all') }
export const reportApi = { list: () => apiClient.get<Report[]>('/reports'), generate: (request: Record<string, unknown>) => apiClient.post<Report>('/reports', request) }
export const healthApi = { get: () => apiClient.get<ServiceHealth[]>('/health/services') }
export const lineageApi = { get: (entityType: string, entityId: string) => apiClient.get<{ nodes: LineageNode[]; relationships: LineageRelationship[] }>(`/lineage${queryString({ entityType, entityId })}`) }
export const predictiveApi = { get: (metric: string) => apiClient.get<Prediction[]>(`/predictions${queryString({ metric })}`) }
export const lifecycleApi = { list: () => apiClient.get<PageResponse<LifecycleRecord>>('/lifecycle') }
export const userApi = { list: () => apiClient.get<PageResponse<User>>('/users'), profile: () => apiClient.get<User>('/users/me'), updateProfile: (payload: Partial<User>) => apiClient.patch<User>('/users/me', payload) }
export const rollbackApi = { preview: (employeeId: string, targetTimestamp: string) => apiClient.get<{ employeeId: string; employeeCode: string; employeeName: string; targetTimestamp: string; currentState: Record<string, unknown>; historicalState: Record<string, unknown>; sourceEventId: string; sourceEventTimestamp: string }>(`/rollback/preview${queryString({ employeeId, targetTimestamp })}`), request: (payload: { employeeId: string; targetTimestamp: string; justification: string; previousState: Record<string, unknown>; targetState: Record<string, unknown> }) => apiClient.post<Approval>('/rollback/request', payload) }
export const complianceApi = {
  getReport: (from: string, to: string) =>
    apiClient.get<{
      generatedAt: string
      from: string
      to: string
      scope: string
      scopeEntityType?: string
      scopeEntityId?: string
      auditActivity: { totalRelevantEvents: number; actions: Record<string, number>; entityTypes: Record<string, number>; status: string }
      integrity: { status: string; message: string; affectedEventId?: string; totalEventsChecked: number; available: boolean }
      anomalySignals: { totalFindings: number; byRiskLevel: Record<string, number>; status: string; note: string }
      approvals: { pendingCount: number; approvedCount: number; rejectedCount: number; byRequestType: Record<string, number>; status: string }
      lineage: { totalEntries: number; entries: unknown[]; status: string }
    }>(`/compliance/reports${queryString({ from, to })}`)
}

export const remediationApi = {
  create: (payload: Record<string, unknown>) =>
    apiClient.post<{ id: string; status: string; auditEventId?: string }>('/remediations', payload),
  list: (q = {}) =>
    apiClient.get<PageResponse<{
      remediationId: string
      sourceFindingEventId: string
      employeeId: string
      recommendedAction: string
      justification: string
      approvalStatus: string
      requester: string
      approver?: string
      requestedAt: string
      decidedAt?: string
      executionStatus: string
      executionResult?: string
      verificationStatus: string
      verificationResult?: string
      verifiedAt?: string
    }>>(`/remediations${queryString(q)}`)
}
