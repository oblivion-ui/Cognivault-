import { ArrowRight, Clock3, Fingerprint, ShieldAlert } from 'lucide-react'
import { Link } from 'react-router-dom'
import { anomalyApi, approvalApi, complianceApi, dashboardApi, employeeApi, healthApi, integrityApi, investigatorApi, remediationApi } from '../api'
import { useAuth, isDemoSession } from '../auth/AuthContext'
import { Badge, DataTable, Metric, PageHeader, Section } from '../components/common/UI'
import { EmptyState, ErrorState, PageSkeleton } from '../components/common/States'
import { useApi } from '../hooks/useApi'
import { demoSummary } from '../mock/demoData'
import { formatDateTime, titleCase } from '../utils/format'

const roleCopy = {
  ADMIN: ['Control center', 'System assurance, workforce governance, and risk posture in one view.'],
  AUDITOR: ['Assurance workspace', 'Follow evidence integrity, anomaly signals, and investigation activity.'],
  MANAGER: ['Team governance', 'Review team access, approvals, and operational risk.'],
  HR: ['Workforce governance', 'Review employee account status, compliance signals, and remediation activity.'],
  COMPLIANCE_OFFICER: ['Compliance workspace', 'Review compliance posture, integrity, risk signals, and remediation activity.'],
  EMPLOYEE: ['My secure workspace', 'Review your permitted compliance activity and account-related governance.'],
} as const

type DashboardData = Awaited<ReturnType<typeof dashboardApi.summary>>

type RoleWorkspaceData = {
  compliance?: Awaited<ReturnType<typeof complianceApi.getReport>>
  employees?: Awaited<ReturnType<typeof employeeApi.list>>
  health?: Awaited<ReturnType<typeof healthApi.get>>
  integrity?: Awaited<ReturnType<typeof integrityApi.latest>>
  investigations?: Awaited<ReturnType<typeof investigatorApi.history>>
  remediations?: Awaited<ReturnType<typeof remediationApi.list>>
}

async function loadDashboard(role: keyof typeof roleCopy): Promise<DashboardData & { roleWorkspace: RoleWorkspaceData }> {
  const summary = await dashboardApi.summary()

  if (isDemoSession()) {
    return { ...summary, roleWorkspace: {} }
  }

  const now = new Date()
  const from = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString()
  const to = now.toISOString()

  const complianceResult = await complianceApi.getReport(from, to)

  const roleWorkspace: RoleWorkspaceData = {
    compliance: complianceResult,
  }

  if (role === 'ADMIN') {
    const [health, employees, integrity] = await Promise.all([
      healthApi.get(),
      employeeApi.list({ page: 0, size: 5 }),
      integrityApi.latest(),
    ])
    roleWorkspace.health = health
    roleWorkspace.employees = employees
    roleWorkspace.integrity = integrity
  }

  if (role === 'AUDITOR') {
    const [integrity, investigations] = await Promise.all([
      integrityApi.latest(),
      investigatorApi.history(),
    ])
    roleWorkspace.integrity = integrity
    roleWorkspace.investigations = investigations
  }

  if (role === 'MANAGER') {
    const [employees, pendingApprovals, anomalySummary] = await Promise.all([
      employeeApi.list({ page: 0, size: 5 }),
      approvalApi.list({ status: 'PENDING', page: 0, size: 1 }),
      anomalyApi.summary(),
    ])
    roleWorkspace.employees = employees
    roleWorkspace.compliance = {
      ...complianceResult,
      approvals: {
        ...complianceResult.approvals,
        pendingCount: pendingApprovals.totalElements,
      },
      anomalySignals: {
        ...complianceResult.anomalySignals,
        totalFindings: anomalySummary.total,
      },
    }
  }

  if (role === 'HR') {
    const [employees, remediations] = await Promise.all([
      employeeApi.list({ page: 0, size: 5 }),
      remediationApi.list({ page: 0, size: 5 }),
    ])
    roleWorkspace.employees = employees
    roleWorkspace.remediations = remediations
  }

  if (role === 'COMPLIANCE_OFFICER') {
    roleWorkspace.remediations = await remediationApi.list({ page: 0, size: 5 })
  }

  return {
    ...summary,
    roleWorkspace,
  }
}

export function DashboardPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'EMPLOYEE'

  const { data, loading, error, retry } = useApi(
    () => isDemoSession()
      ? Promise.resolve(demoSummary)
      : loadDashboard(role),
    [role]
  )

  if (loading) return <PageSkeleton />
  if (error || !data) return <ErrorState message={error?.message} onRetry={retry} />

  const copy = roleCopy[role]
  const roleWorkspace = data.roleWorkspace

  const displayMetrics = data.metrics.map(metric => {
    if (metric.key === 'anomalies' && roleWorkspace.compliance) {
      return {
        ...metric,
        value: roleWorkspace.compliance.anomalySignals.totalFindings,
      }
    }

    if (metric.key === 'approvals' && roleWorkspace.compliance) {
      return {
        ...metric,
        value: roleWorkspace.compliance.approvals.pendingCount,
      }
    }

    return metric
  })

  return (
    <>
      <PageHeader
        eyebrow={copy[0]}
        title={`Good morning, ${user!.name.split(' ')[0]}`}
        description={copy[1]}
        actions={<span className="as-of"><Clock3 /> Live operational view</span>}
      />

      <div className="metrics-grid">
        {displayMetrics.map(metric => (
          <Metric
            key={metric.key}
            label={metric.label}
            value={metric.value}
            detail={metric.change}
            tone={metric.severity}
          />
        ))}
      </div>

      <div className="dashboard-grid">
        <Section title="Audit activity" description="Recorded events over the last seven days">
          <TrendChart data={data.auditTrend || []} />
        </Section>

        <Section title="Risk distribution" description="Backend-classified event risk">
          <RiskDonut data={data.riskDistribution || []} />
        </Section>
      </div>

      <RoleWorkspace role={role} data={roleWorkspace} />

      <Section
        title="Recent security activity"
        description="Latest events visible to your role"
        action={
          <Link className="text-link" to="/audit">
            Open explorer <ArrowRight />
          </Link>
        }
      >
        <DataTable headers={['Event', 'Timestamp', 'Actor', 'Action', 'Risk', 'Integrity']}>
          {data.recentEvents?.map(event => (
            <tr key={event.id}>
              <td>
                <Link className="mono link" to={`/audit/${event.eventId || event.id}`}>
                  {event.eventId || event.id}
                </Link>
              </td>
              <td>{formatDateTime(event.timestamp)}</td>
              <td>{event.actor}</td>
              <td>
                <strong>{titleCase(event.action)}</strong>
                <small>{event.entity}</small>
              </td>
              <td><Badge>{event.riskLevel || 'UNKNOWN'}</Badge></td>
              <td>
                <span className="integrity-cell">
                  <Fingerprint />
                  {event.integrityStatus?.replace('_', ' ') || 'Unavailable'}
                </span>
              </td>
            </tr>
          ))}
        </DataTable>
      </Section>
    </>
  )
}

function TrendChart({ data }: { data: Array<{ label: string; value: number }> }) {
  const max = Math.max(...data.map(d => d.value), 1)

  return (
    <div className="bar-chart" aria-label="Audit activity chart">
      {data.map((d, i) => (
        <div className="bar-column" key={d.label}>
          <span className="bar-value">{d.value.toLocaleString()}</span>
          <div className="bar-track">
            <i
              style={{
                height: `${Math.max(8, d.value / max * 100)}%`,
                animationDelay: `${i * 60}ms`,
              }}
            />
          </div>
          <small>{d.label}</small>
        </div>
      ))}
    </div>
  )
}

function RiskDonut({ data }: { data: Array<{ label: string; value: number }> }) {
  if (!data.length) {
    return (
      <EmptyState
        title="Risk data unavailable"
        description="The risk service has not returned a distribution for this workspace."
      />
    )
  }

  const normalized = data.map(item => ({
    ...item,
    label: item.label.toUpperCase(),
  }))
  const total = normalized.reduce((sum, item) => sum + item.value, 0) || 1
  const low = normalized.find(d => d.label === 'LOW')?.value || 0
  const medium = normalized.find(d => d.label === 'MEDIUM')?.value || 0
  const high = normalized.find(d => d.label === 'HIGH')?.value || 0
  const critical = normalized.find(d => d.label === 'CRITICAL')?.value || 0

  const lowEnd = (low / total) * 100
  const mediumEnd = ((low + medium) / total) * 100
  const highEnd = ((low + medium + high) / total) * 100

  return (
    <div className="risk-visual">
      <div
        className="donut"
        style={{
          background: `conic-gradient(#19c6a3 0 ${lowEnd}%, #f4b85c ${lowEnd}% ${mediumEnd}%, #f06d6d ${mediumEnd}% ${highEnd}%, #9b6df0 ${highEnd}% 100%)`,
        }}
      >
        <div>
          <ShieldAlert />
          <strong>{Math.round((high + critical) / total * 100)}%</strong>
          <span>elevated</span>
        </div>
      </div>

      <div className="legend">
        {normalized.map(d => (
          <div key={d.label}>
            <span className={d.label.toLowerCase()} />
            <p>{titleCase(d.label)}<strong>{d.value}</strong></p>
          </div>
        ))}
      </div>
    </div>
  )
}

function RoleWorkspace({ role, data }: { role: keyof typeof roleCopy; data: RoleWorkspaceData }) {
  const compliance = data.compliance

  return (
    <Section
      title={`${roleCopy[role][0]} signals`}
      description="Live data from services authorized for this role"
    >
      <div className="metrics-grid">
        <Metric
          label="Relevant audit events"
          value={compliance?.auditActivity.totalRelevantEvents ?? 'Unavailable'}
        />
        <Metric
          label="Risk findings"
          value={compliance?.anomalySignals.totalFindings ?? 'Unavailable'}
        />
        <Metric
          label="Pending approvals"
          value={compliance?.approvals.pendingCount ?? 'Unavailable'}
        />
        <Metric
          label="Lineage entries"
          value={compliance?.lineage.totalEntries ?? 'Unavailable'}
        />
      </div>

      {role === 'ADMIN' && (
        <div className="detail-list">
          <p><strong>Services:</strong> {data.health?.length ?? 0} monitored</p>
          <p><strong>Employees:</strong> {data.employees?.totalElements ?? 0} available to this workspace</p>
          <p><strong>Integrity:</strong> {data.integrity?.status ?? compliance?.integrity.status ?? 'Unavailable'}</p>
        </div>
      )}

      {role === 'AUDITOR' && (
        <div className="detail-list">
          <p><strong>Integrity:</strong> {data.integrity?.status ?? compliance?.integrity.status ?? 'Unavailable'}</p>
          <p><strong>Investigations:</strong> {data.investigations?.length ?? 0} recorded</p>
        </div>
      )}

      {role === 'MANAGER' && (
        <div className="detail-list">
          <p><strong>Team employees:</strong> {data.employees?.totalElements ?? 0}</p>
          <p><strong>Risk findings:</strong> {compliance?.anomalySignals.totalFindings ?? 0}</p>
        </div>
      )}

      {role === 'HR' && (
        <div className="detail-list">
          <p><strong>Employee records:</strong> {data.employees?.totalElements ?? 0}</p>
          <p><strong>Recent remediation records:</strong> {data.remediations?.totalElements ?? 0}</p>
        </div>
      )}

      {role === 'COMPLIANCE_OFFICER' && (
        <div className="detail-list">
          <p><strong>Compliance scope:</strong> {compliance?.scope ?? 'Unavailable'}</p>
          <p><strong>Remediation records:</strong> {data.remediations?.totalElements ?? 0}</p>
        </div>
      )}

      {role === 'EMPLOYEE' && (
        <div className="detail-list">
          <p><strong>Compliance scope:</strong> {compliance?.scope ?? 'Unavailable'}</p>
          <p><strong>Relevant activity:</strong> {compliance?.auditActivity.totalRelevantEvents ?? 0} events</p>
        </div>
      )}
    </Section>
  )
}
