import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { AlertOctagon, Check, DatabaseBackup, Eye, RotateCcw, ShieldCheck, X } from 'lucide-react'
import { approvalApi, integrityApi, rollbackApi } from '../../api'
import { isDemoSession, useAuth } from '../../auth/AuthContext'
import { Badge, Button, DataTable, Input, Modal, PageHeader, Section, Select } from '../../components/common/UI'
import { EmptyState, ErrorState, InlineAlert, PageSkeleton } from '../../components/common/States'
import { useApi } from '../../hooks/useApi'
import { demoApprovals, demoIntegrity } from '../../mock/demoData'
import { formatDateTime, titleCase } from '../../utils/format'
import type { Approval } from '../../types/domain'

export function IntegrityPage(){
  const{data,loading,error,retry}=useApi(()=>isDemoSession()?Promise.resolve(demoIntegrity):integrityApi.latest(),[]);const[verifying,setVerifying]=useState(false)
  const verify=async()=>{if(isDemoSession())return;setVerifying(true);try{await integrityApi.verify();retry()}finally{setVerifying(false)}}
  if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;const statusClass=data?.status.toLowerCase()||'unavailable'
  return <><PageHeader eyebrow="Tamper evidence" title="Hash-chain integrity" description="Backend verification status for the append-only audit event chain." actions={<Button onClick={verify} disabled={verifying||isDemoSession()}><ShieldCheck/>{verifying?'Verifying...':'Run verification'}</Button>}/>{isDemoSession()&&<InlineAlert>Demo status is sample data and is not a real hash-chain verification.</InlineAlert>}<div className="integrity-hero"><div className={`integrity-orb ${statusClass}`}><ShieldCheck/></div><div><p>Latest verification</p><h2>{data?.status.replace('_',' ')}</h2><span>{data?.message||'No verification message returned.'}</span></div><dl><div><dt>Events checked</dt><dd>{data?.totalEventsChecked?.toLocaleString()||'Not available'}</dd></div><div><dt>Verified at</dt><dd>{formatDateTime(data?.verifiedAt)}</dd></div><div><dt>Affected event</dt><dd>{data?.affectedEventId||'None reported'}</dd></div></dl></div><Section title="Verification evidence"><dl className="detail-list"><div><dt>Chain position</dt><dd>{data?.chainPosition||'Not provided'}</dd></div><div><dt>Previous hash</dt><dd className="mono">{data?.previousHash||'Not provided'}</dd></div><div><dt>Current hash</dt><dd className="mono">{data?.currentHash||'Not provided'}</dd></div></dl></Section></>
}

export function ApprovalsPage(){
  const{user}=useAuth();const{data,loading,error,retry}=useApi(()=>isDemoSession()?Promise.resolve({content:demoApprovals,page:0,size:20,totalElements:demoApprovals.length,totalPages:1}):approvalApi.list(),[]);const[selected,setSelected]=useState<Approval|null>(null);const[comment,setComment]=useState('');const[decisionError,setDecisionError]=useState('');const[decisionLoading,setDecisionLoading]=useState(false);const[decisionResult,setDecisionResult]=useState<Approval|null>(null)
  const close=()=>{setSelected(null);setComment('');setDecisionError('');setDecisionResult(null)}
  const decide=async(decision:'APPROVE'|'REJECT')=>{if(!selected)return;if(user?.name===selected.requester){setDecisionError('Self-approval is blocked by the frontend and must also be enforced by the backend.');return}if(isDemoSession()){setDecisionError('Demo mode does not submit approval decisions.');return}setDecisionLoading(true);setDecisionError('');try{const response=await approvalApi.decide(selected.id,decision,comment);setDecisionResult(response);retry();if(decision==='REJECT')close()}catch(e){setDecisionError(e instanceof Error?e.message:'The approval decision failed.')}finally{setDecisionLoading(false)}}
  if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;return <><PageHeader eyebrow="Four-eyes control" title="Approval queue" description="Review sensitive actions independently before they can be executed."/><Section>{data?.content.length?<DataTable headers={['Request','Requester','Type','Target','Created','Risk','Status','']}>
  {data.content.map(a=><tr key={a.id}><td className="mono">{a.id}</td><td>{a.requester}</td><td><strong>{titleCase(a.requestType)}</strong></td><td>{a.target}</td><td>{formatDateTime(a.createdAt)}</td><td><Badge>{a.riskLevel||'UNKNOWN'}</Badge></td><td><Badge>{a.status}</Badge></td><td><Button variant="ghost" onClick={()=>setSelected(a)}><Eye/>Review</Button></td></tr>)}</DataTable>:<EmptyState title="No pending approvals" description="The backend returned no approval requests in this view."/>}</Section><Modal open={!!selected} onClose={close} title={`Review ${selected?.id}`} description="Your decision is submitted to the backend and cannot be completed by UI state alone." footer={<><Button variant="danger" disabled={decisionLoading} onClick={()=>decide('REJECT')}><X/>{decisionLoading?'Submitting...':'Reject'}</Button><Button disabled={decisionLoading} onClick={()=>decide('APPROVE')}><Check/>{decisionLoading?'Submitting...':'Approve'}</Button></>}><dl className="modal-details"><dt>Requester</dt><dd>{selected?.requester}</dd><dt>Requested action</dt><dd>{selected?.requestType}</dd><dt>Target</dt><dd>{selected?.target}</dd><dt>Justification</dt><dd>{selected?.justification}</dd></dl>{decisionError&&<InlineAlert tone="danger">{decisionError}</InlineAlert>}{decisionResult?.temporaryPassword&&<InlineAlert tone="success">Temporary password generated for this approved request. This credential is shown only once and is not stored in the approval record. <strong className="mono">{decisionResult.temporaryPassword}</strong></InlineAlert>}<Input label="Decision comment" value={comment} onChange={e=>setComment(e.target.value)} placeholder="Required for rejection or sensitive approvals"/><InlineAlert tone="warning">Self-approval is blocked in the interface and must also be rejected by backend policy.</InlineAlert></Modal></>
}

export function ApprovalDetailPage(){const{approvalId:id}=useParams();const{data,loading,error,retry}=useApi(()=>isDemoSession()?Promise.resolve(demoApprovals.find(a=>a.id===id)||null):approvalApi.get(id||''),[id]);if(loading)return <PageSkeleton/>;if(error)return <ErrorState message={error.message} onRetry={retry}/>;return <><PageHeader eyebrow="Approval evidence" title={data?.id||id||'Approval'} description="Review request state, justification, evidence, and decision history."/><Section>{data?<dl className="detail-list"><div><dt>Requester</dt><dd>{data.requester}</dd></div><div><dt>Target</dt><dd>{data.target}</dd></div><div><dt>Request type</dt><dd>{data.requestType}</dd></div><div><dt>Status</dt><dd><Badge>{data.status}</Badge></dd></div><div><dt>Justification</dt><dd>{data.justification||'Not provided'}</dd></div><div><dt>Created</dt><dd>{formatDateTime(data.createdAt)}</dd></div></dl>:<EmptyState title="Approval not returned" description="The backend returned no approval detail for this identifier."/>}</Section></>}

export function RemediationPage(){return <ActionPage kind="remediation"/>}
export function RollbackPage(){return <RollbackPageImpl/>}

function RollbackPageImpl(){
  const[employeeId,setEmployeeId]=useState('');
  const[targetTimestamp,setTargetTimestamp]=useState('');
  const[justification,setJustification]=useState('');
  const[preview,setPreview]=useState<{employeeId:string;employeeCode:string;employeeName:string;targetTimestamp:string;currentState:Record<string,unknown>;historicalState:Record<string,unknown>;sourceEventId:string;sourceEventTimestamp:string}|null>(null);
  const[loading,setLoading]=useState(false);
  const[error,setError]=useState('');
  const[requesting,setRequesting]=useState(false);
  const[success,setSuccess]=useState(false);

  const loadPreview=async()=>{
    if(!employeeId||!targetTimestamp)return;
    setLoading(true);
    setError('');
    setPreview(null);
    try{
      setPreview(await rollbackApi.preview(employeeId,targetTimestamp));
    }catch(e){
      setError(e instanceof Error?e.message:'Unable to load rollback preview');
    }finally{
      setLoading(false);
    }
  };

  const requestRollback=async()=>{
    if(!preview||!justification)return;
    setRequesting(true);
    setError('');
    try{
      await rollbackApi.request({
        employeeId:preview.employeeId,
        targetTimestamp:preview.targetTimestamp,
        justification,
        previousState:preview.currentState,
        targetState:preview.historicalState
      });
      setSuccess(true);
      setPreview(null);
      setEmployeeId('');
      setTargetTimestamp('');
      setJustification('');
    }catch(e){
      setError(e instanceof Error?e.message:'Rollback request failed');
    }finally{
      setRequesting(false);
    }
  };

  return <>
    <PageHeader eyebrow="Governed corrective action" title="Rollback" description="Restore an approved entity snapshot through backend-authorized rollback."/>
    <InlineAlert tone="warning">Rollback requires Four-Eyes approval. No state is changed until a second authorized user approves the request.</InlineAlert>
    <div className="detail-grid">
      <Section title="Select target and version">
        <div className="form-stack">
          <Input label="Employee ID" value={employeeId} onChange={e=>setEmployeeId(e.target.value)} placeholder="Enter employee UUID"/>
          <Input label="Target timestamp" value={targetTimestamp} onChange={e=>setTargetTimestamp(e.target.value)} placeholder="ISO 8601 timestamp (e.g., 2026-09-26T07:45:00Z)"/>
          <Button onClick={loadPreview} disabled={loading||!employeeId||!targetTimestamp}><RotateCcw/>{loading?'Loading...':'Preview rollback'}</Button>
        </div>
      </Section>
      {error&&<InlineAlert tone="danger">{error}</InlineAlert>}
      {preview&&<Section title="State comparison">
        <div className="compare-state">
          <div><span>Employee</span><strong>{preview.employeeName} ({preview.employeeCode})</strong></div>
          <div><span>Source event</span><strong className="mono">{preview.sourceEventId}</strong></div>
          <div><span>Source timestamp</span><strong>{formatDateTime(preview.sourceEventTimestamp)}</strong></div>
        </div>
        <Section title="Current state"><pre className="mono">{JSON.stringify(preview.currentState,null,2)}</pre></Section>
        <Section title="Target state"><pre className="mono">{JSON.stringify(preview.historicalState,null,2)}</pre></Section>
        <Input label="Justification" value={justification} onChange={e=>setJustification(e.target.value)} placeholder="Provide an auditable business justification for this rollback"/>
        <Button onClick={requestRollback} disabled={requesting||!justification}><DatabaseBackup/>{requesting?'Requesting...':'Request rollback approval'}</Button>
      </Section>}
      {success&&<InlineAlert tone="success">Rollback request submitted. It will appear in the approval queue for a second authorized user to review and approve.</InlineAlert>}
    </div>
  </>
}

function ActionPage({kind}:{kind:'remediation'|'rollback'}){
  const[confirm,setConfirm]=useState(false);
  const rollback=kind==='rollback';
  return <><PageHeader eyebrow="Governed corrective action" title={rollback?'Rollback':'Remediation'} description={rollback?'Restore an approved entity snapshot through backend-authorized rollback.':'Plan and execute approved corrective actions with full evidence traceability.'}/><InlineAlert tone="warning">No state is changed locally. Approval and backend authorization are required before execution.</InlineAlert><div className="detail-grid"><Section title={rollback?'Select target and version':'Corrective action'}><div className="form-stack"><Select label="Target entity"><option value="">Select from backend results</option></Select>{rollback&&<Select label="Target snapshot"><option value="">Select an available snapshot</option></Select>}<Select label="Action type"><option>{rollback?'RESTORE_SNAPSHOT':'ACCOUNT_ACTION'}</option><option>{rollback?'REVERT_FIELDS':'CORRECTIVE_ACTION'}</option></Select><Input label="Reason" placeholder="Provide an auditable business justification"/><Button onClick={()=>setConfirm(true)}>{rollback?<RotateCcw/>:<DatabaseBackup/>}Review action</Button></div></Section><Section title="State comparison"><div className="compare-state"><div><span>Current state</span><strong>Not selected</strong></div><div><span>Target state</span><strong>Not selected</strong></div></div><EmptyState title="Select a backend record" description="Current and target state are displayed only from authoritative snapshots."/></Section></div><Modal open={confirm} onClose={()=>setConfirm(false)} title={`Confirm ${kind}`} description="This is a sensitive, auditable operation." footer={<><Button variant="ghost" onClick={()=>setConfirm(false)}>Cancel</Button><Button variant="danger" disabled><AlertOctagon/>Await verified backend selection</Button></>}><p className="modal-note">The current UI does not invent target identifiers or claim execution. Approval and backend authorization are required before any state change.</p></Modal></>
}
