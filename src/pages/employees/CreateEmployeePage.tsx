import { useMemo, useState, type FormEvent } from 'react'
import { Check, ChevronLeft, ChevronRight, ClipboardCheck, RefreshCw, UserPlus } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { employeeApi, type CreateEmployeePayload } from '../../api'
import { isDemoSession } from '../../auth/AuthContext'
import { Button, Input, PageHeader, Select, Section } from '../../components/common/UI'
import { InlineAlert } from '../../components/common/States'

const steps = ['Personal information', 'Employment', 'Account & role', 'Credentials', 'Review']

type FormData = {
  firstName: string
  lastName: string
  employeeId: string
  email: string
  phone: string
  address: string
  department: string
  designation: string
  manager: string
  joiningDate: string
  employmentType: string
  location: string
  username: string
  role: string
  accountStatus: string
  method: 'SET' | 'GENERATE'
  password: string
  confirm: string
  forceChange: boolean
}

const initial: FormData = {
  firstName: '',
  lastName: '',
  employeeId: '',
  email: '',
  phone: '',
  address: '',
  department: '',
  designation: '',
  manager: '',
  joiningDate: '',
  employmentType: 'FULL_TIME',
  location: '',
  username: '',
  role: 'EMPLOYEE',
  accountStatus: 'PENDING',
  method: 'GENERATE',
  password: '',
  confirm: '',
  forceChange: true
}

export function CreateEmployeePage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<{ id: string; status?: string } | null>(null)

  const set = (key: keyof FormData, value: string | boolean) =>
    setForm(v => ({ ...v, [key]: value }))

  const valid = useMemo(
    () =>
      step === 0
        ? !!(
            form.firstName &&
            form.lastName &&
            form.employeeId &&
            /^\S+@\S+\.\S+$/.test(form.email)
          )
        : step === 1
          ? !!(form.department && form.designation && form.joiningDate)
          : step === 2
            ? !!(form.username && form.role)
            : step === 3
              ? form.method === 'GENERATE'
              : true,
    [step, form]
  )

  const submit = async () => {
    setSubmitting(true)
    setError('')

    const payload: CreateEmployeePayload = {
      personal: {
        firstName: form.firstName,
        lastName: form.lastName,
        employeeId: form.employeeId,
        email: form.email,
        phone: form.phone,
        address: form.address
      },
      employment: {
        department: form.department,
        designation: form.designation,
        manager: form.manager,
        joiningDate: form.joiningDate,
        employmentType: form.employmentType,
        location: form.location
      },
      account: {
        username: form.username,
        role: form.role,
        accountStatus: form.accountStatus
      },
      credential: {
        method: 'GENERATE',
        forceChange: form.forceChange
      }
    }

    try {
      if (isDemoSession()) {
        setCreated({ id: 'DEMO-NOT-SUBMITTED', status: 'DEMO' })
        return
      }

      const result = await employeeApi.create(payload)

      setCreated({
        id: result.id,
        status: result.status
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to submit employee creation request.')
    } finally {
      setSubmitting(false)
    }
  }

  if (created) {
    const demo = isDemoSession()

    return (
      <>
        <PageHeader
          eyebrow="Approval workflow"
          title={demo ? 'Demo submission not sent' : 'Approval requested'}
          description={
            demo
              ? 'Demo mode does not fabricate backend approval results. Connect a backend to submit this workflow.'
              : 'The employee creation request is pending four-eyes approval. No employee or credential has been created yet.'
          }
        />

        <Section>
          <div className="success-panel">
            <Check />
            <h2>
              {form.firstName} {form.lastName}
            </h2>

            <p>
              Request ID: <strong>{created.id}</strong>
            </p>

            {!demo && (
              <>
                <p className="confirmed-note">
                  <Check /> Status: <strong>{created.status || 'PENDING'}</strong>
                </p>
                <p className="confirmed-note">
                  A different authorized administrator must approve this request before the employee account is created.
                </p>
              </>
            )}

            <Button onClick={() => navigate('/employees')}>
              Return to employees
            </Button>
          </div>
        </Section>
      </>
    )
  }

  return (
    <>
      <PageHeader
        eyebrow="Employee lifecycle"
        title="Create employee"
        description="Provision identity, employment, access, and initial credentials through one controlled workflow."
      />

      <div className="stepper">
        {steps.map((label, i) => (
          <div className={i === step ? 'active' : i < step ? 'complete' : ''} key={label}>
            <span>{i < step ? <Check /> : i + 1}</span>
            <p>{label}</p>
          </div>
        ))}
      </div>

      {error && <InlineAlert tone="danger">{error}</InlineAlert>}

      <Section>
        <form onSubmit={(e: FormEvent) => e.preventDefault()}>
          {step === 0 && (
            <div className="form-grid">
              <Input label="First name *" value={form.firstName} onChange={e => set('firstName', e.target.value)} />
              <Input label="Last name *" value={form.lastName} onChange={e => set('lastName', e.target.value)} />
              <Input label="Employee ID *" value={form.employeeId} onChange={e => set('employeeId', e.target.value)} hint="Validated for uniqueness by the backend on submission." />
              <Input label="Work email *" type="email" value={form.email} onChange={e => set('email', e.target.value)} />
              <Input label="Phone" value={form.phone} onChange={e => set('phone', e.target.value)} />
              <Input label="Address" value={form.address} onChange={e => set('address', e.target.value)} />
            </div>
          )}

          {step === 1 && (
            <div className="form-grid">
              <Input label="Department *" value={form.department} onChange={e => set('department', e.target.value)} />
              <Input label="Designation *" value={form.designation} onChange={e => set('designation', e.target.value)} />
              <Input label="Manager" value={form.manager} onChange={e => set('manager', e.target.value)} />
              <Input label="Joining date *" type="date" value={form.joiningDate} onChange={e => set('joiningDate', e.target.value)} />
              <Select label="Employment type" value={form.employmentType} onChange={e => set('employmentType', e.target.value)}>
                <option value="FULL_TIME">Full time</option>
                <option value="PART_TIME">Part time</option>
                <option value="CONTRACT">Contract</option>
              </Select>
              <Input label="Location" value={form.location} onChange={e => set('location', e.target.value)} />
            </div>
          )}

          {step === 2 && (
            <div className="form-grid">
              <Input label="Username *" value={form.username} onChange={e => set('username', e.target.value)} />
              <Select label="Role *" value={form.role} onChange={e => set('role', e.target.value)}>
                <option>EMPLOYEE</option>
                <option>MANAGER</option>
                <option>AUDITOR</option>
                <option>ADMIN</option>
              </Select>
              <Select label="Initial account status" value={form.accountStatus} onChange={e => set('accountStatus', e.target.value)}>
                <option>PENDING</option>
                <option>ACTIVE</option>
                <option>INACTIVE</option>
              </Select>
              <div className="info-block">
                <strong>Backend-managed permissions</strong>
                <p>Permissions are resolved from the selected role by the backend. The frontend does not invent or elevate permissions.</p>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="credential-panel">
              <div className="choice-row">
                <button type="button" className="selected" onClick={() => set('method', 'GENERATE')}>
                  <RefreshCw />
                  <strong>Generate temporary password</strong>
                  <span>The credential will be generated only when the approval is executed.</span>
                </button>
              </div>

              <label className="check">
                <input
                  type="checkbox"
                  checked={form.forceChange}
                  onChange={e => set('forceChange', e.target.checked)}
                />
                <span>
                  <strong>Require password change at first login</strong>
                  <small>Recommended for all initial credentials.</small>
                </span>
              </label>

              <InlineAlert tone="warning">
                Employee creation requires four-eyes approval. No password is generated or stored at request time.
              </InlineAlert>
            </div>
          )}

          {step === 4 && <Review form={form} />}

          <div className="wizard-actions">
            <Button type="button" variant="secondary" disabled={step === 0} onClick={() => setStep(v => v - 1)}>
              <ChevronLeft />
              Back
            </Button>

            {step < 4 ? (
              <Button type="button" disabled={!valid} onClick={() => setStep(v => v + 1)}>
                Continue
                <ChevronRight />
              </Button>
            ) : (
              <Button type="button" disabled={submitting} onClick={submit}>
                <UserPlus />
                {submitting ? 'Submitting...' : 'Request approval'}
              </Button>
            )}
          </div>
        </form>
      </Section>
    </>
  )
}

function Review({ form }: { form: FormData }) {
  const groups = [
    [
      'Personal information',
      [
        ['Name', `${form.firstName} ${form.lastName}`],
        ['Employee ID', form.employeeId],
        ['Email', form.email]
      ]
    ],
    [
      'Employment',
      [
        ['Department', form.department],
        ['Designation', form.designation],
        ['Manager', form.manager || 'Not assigned'],
        ['Joining date', form.joiningDate]
      ]
    ],
    [
      'Account & credentials',
      [
        ['Username', form.username],
        ['Role', form.role],
        ['Account status', form.accountStatus],
        ['Credential method', 'Backend-generated temporary password'],
        ['First-login change', form.forceChange ? 'Required' : 'Not required']
      ]
    ]
  ] as const

  return (
    <div className="review">
      <div className="review-intro">
        <ClipboardCheck />
        <div>
          <h2>Review provisioning request</h2>
          <p>Confirm all details before sending this security-sensitive operation for approval.</p>
        </div>
      </div>

      {groups.map(([title, items]) => (
        <div className="review-group" key={title}>
          <h3>{title}</h3>
          {items.map(([label, value]) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}
