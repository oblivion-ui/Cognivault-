import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Save } from 'lucide-react'
import { employeeApi } from '../../api'
import { isDemoSession } from '../../auth/AuthContext'
import { Button, Input, PageHeader, Select, Section } from '../../components/common/UI'
import { ErrorState, InlineAlert, PageSkeleton } from '../../components/common/States'
import { useApi } from '../../hooks/useApi'
import { demoEmployees } from '../../mock/demoData'
import type { Approval, Employee, Role } from '../../types/domain'

type EditForm={firstName:string;lastName:string;email:string;phone:string;department:string;designation:string;manager:string;role:Role}
const emptyForm:EditForm={firstName:'',lastName:'',email:'',phone:'',department:'',designation:'',manager:'',role:'EMPLOYEE'}

export function EditEmployeePage(){
  const{ id }=useParams();const navigate=useNavigate();const employee=useApi(()=>isDemoSession()?Promise.resolve(demoEmployees.find(e=>e.id===id)||null):employeeApi.get(id||''),[id]);const[form,setForm]=useState<EditForm>(emptyForm);const[error,setError]=useState('');const[saved,setSaved]=useState<Approval|null>(null);const[saving,setSaving]=useState(false)
  useEffect(()=>{if(employee.data){const e=employee.data;setForm({firstName:e.firstName,lastName:e.lastName,email:e.email,phone:e.phone||'',department:e.department||'',designation:e.designation||'',manager:e.manager||'',role:e.role})}},[employee.data])
  const update=(key:keyof EditForm,value:string)=>setForm(v=>({...v,[key]:value}))
  const submit=async()=>{if(isDemoSession()){setError('Demo mode does not submit employee edits. Connect the backend to perform this action.');return}setSaving(true);setError('');setSaved(null);try{const approval=await employeeApi.update(id||'',form);setSaved(approval)}catch(e){setError(e instanceof Error?e.message:'The employee update failed.')}finally{setSaving(false)}}
  if(employee.loading)return <PageSkeleton/>;if(employee.error||!employee.data)return <ErrorState message={employee.error?.message||'Employee not found.'} onRetry={employee.retry} />
  const e=employee.data as Employee
  return <><PageHeader eyebrow="Auditable workforce change" title="Edit employee" description={`Update permitted fields for ${e.employeeId}. Changes are submitted to backend authorization and audit.`}/><InlineAlert tone="warning">Role, department, and manager changes may require four-eyes approval based on backend policy.</InlineAlert>{error&&<InlineAlert tone="danger">{error}</InlineAlert>}{saved&&<InlineAlert tone="success">Approval requested. Request ID: <strong>{saved.id}</strong>. Status: <strong>{saved.status}</strong>. The employee record will change only after an authorized second user approves this request.</InlineAlert>}<Section><div className="form-grid"><Input label="First name" value={form.firstName} onChange={e=>update('firstName',e.target.value)}/><Input label="Last name" value={form.lastName} onChange={e=>update('lastName',e.target.value)}/><Input label="Work email" type="email" value={form.email} onChange={e=>update('email',e.target.value)}/><Input label="Phone" value={form.phone} onChange={e=>update('phone',e.target.value)}/><Input label="Department" value={form.department} onChange={e=>update('department',e.target.value)}/><Input label="Designation" value={form.designation} onChange={e=>update('designation',e.target.value)}/><Input label="Manager" value={form.manager} onChange={e=>update('manager',e.target.value)}/><Select label="Role" value={form.role} onChange={e=>update('role',e.target.value)}><option>EMPLOYEE</option><option>MANAGER</option><option>AUDITOR</option><option>ADMIN</option></Select></div><div className="wizard-actions"><Button disabled={saving} onClick={submit}><Save/>{saving?'Submitting...':'Submit changes'}</Button><Button variant="secondary" onClick={()=>navigate(`/employees/${e.id}`)}>Cancel</Button></div></Section></>
}


\n