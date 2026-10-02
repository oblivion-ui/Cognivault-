export type Role = 'ADMIN' | 'HR' | 'MANAGER' | 'AUDITOR' | 'COMPLIANCE_OFFICER' | 'EMPLOYEE'
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'DEACTIVATED' | 'LOCKED' | 'PENDING'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'UNKNOWN'
export type IntegrityStatus = 'VERIFIED' | 'FAILED' | 'NOT_VERIFIED' | 'UNAVAILABLE'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface User { id: string; username: string; name: string; email: string; role: Role; department?: string; accountStatus: AccountStatus; lastLogin?: string; mustChangePassword?: boolean }
export interface Department { id: string; name: string; code?: string }
export interface Employee { id: string; employeeId: string; firstName: string; lastName: string; name?: string; email: string; phone?: string; address?: string; department?: string; departmentId?: string; designation?: string; manager?: string; managerId?: string; joiningDate?: string; employmentType?: string; location?: string; role: Role; username?: string; accountStatus: AccountStatus; credentialStatus?: string; riskLevel?: RiskLevel; lastLogin?: string; createdAt?: string }
export interface ChangedField { field: string; before: unknown; after: unknown }
export interface AuditSnapshot { id?: string; data: Record<string, unknown>; capturedAt?: string }
export interface AuditEvent { id: string; eventId?: string; timestamp: string; actor: string; actorId?: string; employee?: string; action: string; entity: string; entityId?: string; module?: string; status?: string; riskLevel?: RiskLevel; anomalyScore?: number; anomalous?: boolean; integrityStatus?: IntegrityStatus; hash?: string; previousHash?: string; chainPosition?: number; beforeSnapshot?: AuditSnapshot; afterSnapshot?: AuditSnapshot; changedFields?: ChangedField[]; requestId?: string; ipAddress?: string }
export interface SHAPExplanation { feature: string; contribution: number; direction: 'POSITIVE_CONTRIBUTION' | 'NEGATIVE_CONTRIBUTION' | 'NEUTRAL'; explanation?: string }
export interface Anomaly { id: string; eventId: string; employee?: string; score: number; riskLevel: RiskLevel; flaggedAt: string; model?: string; indicators?: string[]; explanations?: SHAPExplanation[] }
export interface Approval { id: string; requester: string; requestType: string; target: string; createdAt: string; status: ApprovalStatus; requiredApprover?: string; riskLevel?: RiskLevel; justification?: string; previousState?: unknown; requestedState?: unknown; temporaryPassword?: string; history?: Array<{ action: string; actor: string; timestamp: string; comment?: string }> }
export interface Notification { id: string; title: string; message: string; timestamp: string; type: string; severity: RiskLevel | 'INFO'; read: boolean; link?: string }
export interface IntegrityResult { status: IntegrityStatus; verifiedAt?: string; totalEventsChecked?: number; affectedEventId?: string; previousHash?: string; currentHash?: string; chainPosition?: number; message?: string }
export type ServiceReadiness = 'STARTING' | 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'MISCONFIGURED' | 'NOT_CONFIGURED' | 'UP' | 'DOWN' | 'UNKNOWN'
export interface ServiceHealth { service: string; status: ServiceReadiness; checkedAt?: string; latencyMs?: number; details?: string }
export interface Evidence { id: string; eventId?: string; title: string; excerpt?: string; timestamp?: string; source?: string }
export interface Investigation { id?: string; query: string; answer?: string | null; status?: 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'NOT_CONFIGURED' | 'EMPTY' | 'ERROR'; evidence?: Evidence[]; confidence?: number; limitations?: string[]; accessFilter?: string; createdAt?: string }
export interface Report { id: string; name: string; category: string; status: string; generatedAt?: string; format?: string; aiNarrative?: string }
export interface LineageNode { id: string; label: string; type: string; metadata?: Record<string, unknown> }
export interface LineageRelationship { id: string; source: string; target: string; type: string }
export interface Prediction { timestamp: string; value: number; lowerBound?: number; upperBound?: number; metric: string; kind: 'HISTORICAL' | 'FORECAST' }
export interface LifecycleRecord { id: string; subject: string; policy: string; status: string; retentionUntil?: string; lastAction?: string }
export type PageResponse<T> = { content: T[]; size: number; totalElements: number; totalPages: number } & ({ page: number; number?: number } | { page?: number; number: number })
export interface DashboardSummary { metrics: Array<{ key: string; label: string; value: number | string; change?: string; severity?: RiskLevel }>; auditTrend?: Array<{ label: string; value: number }>; riskDistribution?: Array<{ label: string; value: number }>; recentEvents?: AuditEvent[] }
