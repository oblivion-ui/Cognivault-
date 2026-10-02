import { useState } from 'react'
import { Bell, CheckCheck, Download, ExternalLink, FilePlus2, HeartPulse } from 'lucide-react'
import { Link } from 'react-router-dom'
import { complianceApi, healthApi, lifecycleApi, notificationApi } from '../../api'
import { isDemoSession } from '../../auth/AuthContext'
import { Badge, Button, DataTable, Input, Metric, PageHeader, Section, Select } from '../../components/common/UI'
import { EmptyState, ErrorState, InlineAlert, PageSkeleton } from '../../components/common/States'
import { useApi } from '../../hooks/useApi'
import { demoHealth, demoNotifications } from '../../mock/demoData'
import { formatDateTime } from '../../utils/format'

export function ReportsPage(){
  const [startDate,setStartDate]=useState(()=>{const d=new Date(Date.now()-30*24*60*60*1000);return d.toISOString().slice(0,10)})
  const [endDate,setEndDate]=useState(()=>new Date().toISOString().slice(0,10))
  const [refreshing,setRefreshing]=useState(false)
  const [submitError,setSubmitError]=useState('')

  const toStartInstant=(value:string)=>new Date(`${value}T00:00:00.000Z`).toISOString()
  const toEndInstant=(value:string)=>new Date(`${value}T23:59:59.999Z`).toISOString()

  const {data,loading,error,retry}=useApi(
    ()=>isDemoSession()
      ? Promise.resolve(undefined)
      : complianceApi.getReport(toStartInstant(startDate),toEndInstant(endDate)),
    [startDate,endDate]
  )

  const refresh=async()=>{
    setRefreshing(true)
    setSubmitError('')
    try{
      await retry()
    }catch(e){
      setSubmitError(e instanceof Error?e.message:'The compliance report could not be refreshed.')
    }finally{
      setRefreshing(false)
    }
  }

  if(loading)return <PageSkeleton/>
  if(error)return <ErrorState message={error.message} onRetry={retry}/>

  return <>
    <PageHeader
      eyebrow="Evidence reporting"
      title="Reports"
      description="Review factual compliance evidence from the authoritative backend."
      actions={<Button disabled={refreshing||isDemoSession()} onClick={refresh}><FilePlus2/>{refreshing?'Refreshing...':'Refresh report'}</Button>}
    />

    {isDemoSession()
      ? <InlineAlert>Demo mode does not fabricate compliance reports. Connect the backend to view real evidence.</InlineAlert>
      : <Section title="Report period">
          <div className="form-grid">
            <Input label="Start date" type="date" value={startDate} onChange={e=>setStartDate(e.target.value)}/>
            <Input label="End date" type="date" value={endDate} onChange={e=>setEndDate(e.target.value)}/>
          </div>
          <InlineAlert>Report values come directly from audit activity, integrity verification, anomaly findings, approvals, and lineage records. ML findings are signals, not proof of fraud.</InlineAlert>
          {submitError&&<InlineAlert tone="danger">{submitError}</InlineAlert>}
        </Section>
    }

    {data&&<Section title="Compliance evidence">
      <div className="metric-grid">
        <Metric label="Relevant audit events" value={data.auditActivity.totalRelevantEvents}/>
        <Metric label="Risk findings" value={data.anomalySignals.totalFindings}/>
        <Metric label="Pending approvals" value={data.approvals.pendingCount}/>
        <Metric label="Lineage entries" value={data.lineage.totalEntries}/>
      </div>

      <div className="form-grid">
        <div>
          <strong>Audit activity</strong>
          <p>Status: {data.auditActivity.status}</p>
          <p>Actions: {Object.entries(data.auditActivity.actions).map(([k,v])=>`${k}: ${v}`).join(', ')||'None reported'}</p>
        </div>
        <div>
          <strong>Integrity</strong>
          <p>Status: <Badge>{data.integrity.status}</Badge></p>
          <p>{data.integrity.message}</p>
          <p>Events checked: {data.integrity.totalEventsChecked}</p>
        </div>
        <div>
          <strong>Anomaly signals</strong>
          <p>Status: {data.anomalySignals.status}</p>
          <p>{data.anomalySignals.note}</p>
        </div>
        <div>
          <strong>Approvals</strong>
          <p>Pending: {data.approvals.pendingCount}</p>
          <p>Approved: {data.approvals.approvedCount}</p>
          <p>Rejected: {data.approvals.rejectedCount}</p>
        </div>
      </div>
    </Section>}

    {!data&&!isDemoSession()&&<EmptyState title="No compliance report returned" description="The backend returned no compliance evidence for the selected period."/>}
  </>
}

export function ServiceHealthPage(){const{data,loading,error,retry}=useApi(()=>isDemoSession()?Promise.resolve(demoHealth):healthApi.get(),[]);if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;return <><PageHeader eyebrow="Platform operations" title="Service health" description="Live health signals reported by the configured platform health endpoint."/>{isDemoSession()&&<InlineAlert>These are clearly isolated demo values, not real infrastructure health.</InlineAlert>}<div className="health-list">{data?.length?data.map(service=><div key={service.service}><span className={`health-icon ${service.status.toLowerCase()}`}><HeartPulse/></span><div><strong>{service.service}</strong><small>{service.details||'No issue details reported'}</small></div><Badge>{service.status}</Badge><span>{service.latencyMs!==undefined?`${service.latencyMs} ms`:'Latency unavailable'}</span><time>{formatDateTime(service.checkedAt)}</time></div>):<EmptyState title="No service health returned" description="The configured health endpoint returned no service records."/>}</div></>}

export function NotificationsPage(){const{data,loading,error,retry,setData}=useApi(()=>isDemoSession()?Promise.resolve({content:demoNotifications,page:0,size:20,totalElements:demoNotifications.length,totalPages:1}):notificationApi.list(),[]);const[marking,setMarking]=useState(false);const[markError,setMarkError]=useState('');const markAll=async()=>{if(isDemoSession()){setMarkError('Demo mode does not submit notification changes.');return}setMarking(true);setMarkError('');try{await notificationApi.markAllRead();setData(current=>current?{...current,content:current.content.map(n=>({...n,read:true}))}:current)}catch(e){setMarkError(e instanceof Error?e.message:'Notifications could not be marked read.')}finally{setMarking(false)}};if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;return <><PageHeader eyebrow="Security inbox" title="Notifications" description="Approval, anomaly, integrity, account, and platform updates." actions={<Button variant="secondary" disabled={marking} onClick={markAll}><CheckCheck/>{marking?'Updating...':'Mark all read'}</Button>}/>{markError&&<InlineAlert tone="danger">{markError}</InlineAlert>}<Section>{data?.content.length?<div className="notification-list">{data.content.map(n=><article className={n.read?'':'unread'} key={n.id}><span className="notification-icon"><Bell/></span><div><div><strong>{n.title}</strong><Badge>{n.severity}</Badge></div><p>{n.message}</p><time>{formatDateTime(n.timestamp)}</time></div>{n.link&&<Link to={n.link} aria-label="Open related item"><ExternalLink/></Link>}</article>)}</div>:<EmptyState title="No notifications" description="You are all caught up."/>}</Section></>}

export function LifecyclePage(){const{data,loading,error,retry}=useApi(()=>isDemoSession()?Promise.resolve({content:[],page:0,size:20,totalElements:0,totalPages:0}):lifecycleApi.list(),[]);if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;return <><PageHeader eyebrow="Data governance" title="Lifecycle & retention" description="Review backend-defined retention, archival, and governance states."/><Section>{data?.content.length?<DataTable headers={['Subject','Policy','Status','Retain until','Last action']}>{data.content.map(x=><tr key={x.id}><td>{x.subject}</td><td>{x.policy}</td><td><Badge>{x.status}</Badge></td><td>{x.retentionUntil||'Not provided'}</td><td>{x.lastAction||'Not provided'}</td></tr>)}</DataTable>:<EmptyState title="No lifecycle records available" description="No governance records were returned by the backend."/>}</Section></>}
