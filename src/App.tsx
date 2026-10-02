import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { RoleGuard } from './auth/RoleGuard'
import { AppShell } from './components/layout/AppShell'
import { LoginPage } from './pages/LoginPage'
import { ForcePasswordPage } from './pages/PasswordPage'
import { DashboardPage } from './pages/DashboardPage'
import { EmployeesPage } from './pages/employees/EmployeesPage'
import { CreateEmployeePage } from './pages/employees/CreateEmployeePage'
import { EmployeeDetailPage } from './pages/employees/EmployeeDetailPage'
import { EditEmployeePage } from './pages/employees/EditEmployeePage'
import { AuditDetailPage, AuditExplorerPage } from './pages/audit/AuditPages'
import { AnomaliesPage, InvestigatorPage } from './pages/risk/RiskPages'
import { ApprovalDetailPage, ApprovalsPage, IntegrityPage, RemediationPage, RollbackPage } from './pages/governance/GovernancePages'
import { LifecyclePage, NotificationsPage, ReportsPage, ServiceHealthPage } from './pages/platform/PlatformPages'
import { LineagePage, PredictivePage } from './pages/intelligence/IntelligencePages'
import { AdminPage, AdminUsersPage, NotFoundPage, ProfilePage, RequestsPage } from './pages/account/AccountPages'
import type { Role } from './types/domain'

const guard=(roles:Role[],node:ReactNode)=><RoleGuard allow={roles}>{node}</RoleGuard>
export default function App(){return <Routes>
  <Route path="/login" element={<LoginPage/>}/>
  <Route element={<ProtectedRoute/>}>
    <Route path="/force-password-change" element={<ForcePasswordPage/>}/>
    <Route element={<AppShell/>}>
      <Route index element={<Navigate to="/dashboard" replace/>}/><Route path="/dashboard" element={<DashboardPage/>}/>
      <Route path="/employees" element={guard(['ADMIN','HR','MANAGER'],<EmployeesPage/>)}/><Route path="/employees/new" element={guard(['ADMIN','HR'],<CreateEmployeePage/>)}/><Route path="/employees/:id" element={guard(['ADMIN','HR','MANAGER'],<EmployeeDetailPage/>)}/><Route path="/employees/:id/edit" element={guard(['ADMIN','HR','MANAGER'],<EditEmployeePage/>)}/><Route path="/employee-requests" element={guard(['ADMIN','MANAGER','EMPLOYEE'],<RequestsPage/>)}/>
      <Route path="/audit" element={guard(['ADMIN','AUDITOR','MANAGER'],<AuditExplorerPage/>)}/><Route path="/audit/:id" element={guard(['ADMIN','AUDITOR','MANAGER'],<AuditDetailPage/>)}/><Route path="/anomalies" element={guard(['ADMIN','AUDITOR','MANAGER'],<AnomaliesPage/>)}/><Route path="/investigator" element={guard(['ADMIN','AUDITOR','COMPLIANCE_OFFICER'],<InvestigatorPage/>)}/><Route path="/integrity" element={guard(['ADMIN','AUDITOR'],<IntegrityPage/>)}/>
      <Route path="/approvals" element={guard(['ADMIN','MANAGER'],<ApprovalsPage/>)}/><Route path="/approvals/:id" element={guard(['ADMIN','MANAGER'],<ApprovalDetailPage/>)}/><Route path="/remediation" element={guard(['ADMIN','AUDITOR'],<RemediationPage/>)}/><Route path="/rollback" element={guard(['ADMIN'],<RollbackPage/>)}/>
      <Route path="/reports" element={guard(['ADMIN','AUDITOR','MANAGER'],<ReportsPage/>)}/><Route path="/service-health" element={guard(['ADMIN'],<ServiceHealthPage/>)}/><Route path="/notifications" element={<NotificationsPage/>}/><Route path="/lineage" element={guard(['ADMIN','AUDITOR'],<LineagePage/>)}/><Route path="/predictive" element={guard(['ADMIN','AUDITOR'],<PredictivePage/>)}/><Route path="/lifecycle" element={guard(['ADMIN','HR','MANAGER'],<LifecyclePage/>)}/>
      <Route path="/profile" element={<ProfilePage/>}/><Route path="/admin" element={guard(['ADMIN'],<AdminPage/>)}/><Route path="/admin/users" element={guard(['ADMIN'],<AdminUsersPage/>)}/><Route path="/admin/employees" element={<Navigate to="/employees" replace/>}/><Route path="*" element={<NotFoundPage/>}/>
    </Route>
  </Route>
  <Route path="*" element={<Navigate to="/login" replace/>}/>
</Routes>}

\n