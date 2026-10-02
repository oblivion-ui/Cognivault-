import { AlertTriangle, Inbox, LoaderCircle, LockKeyhole, RefreshCw, ServerOff } from 'lucide-react'
import type { ReactNode } from 'react'

export function Spinner({className=''}:{className?:string}){return <LoaderCircle className={`animate-spin ${className}`} aria-hidden="true"/>}
export function FullPageLoader(){return <div className="grid min-h-screen place-items-center bg-canvas"><div className="text-center"><Spinner className="mx-auto mb-3 size-7 text-cyan"/><p className="text-sm text-muted">Securing your workspace...</p></div></div>}
export function Skeleton({className=''}:{className?:string}){return <div className={`skeleton ${className}`} aria-hidden="true"/>}
export function PageSkeleton(){return <div className="space-y-6"><Skeleton className="h-9 w-64"/><div className="grid grid-cols-4 gap-4">{[1,2,3,4].map(i=><Skeleton key={i} className="h-28"/>)}</div><Skeleton className="h-96"/></div>}
export function StatePanel({icon,title,description,action}:{icon?:ReactNode;title:string;description:string;action?:ReactNode}){return <div className="state-panel">{icon||<Inbox className="size-7"/>}<h3>{title}</h3><p>{description}</p>{action}</div>}
export function EmptyState({title='Nothing to show',description='No records match the current view.'}:{title?:string;description?:string}){return <StatePanel title={title} description={description}/>}
export function ErrorState({message='Unable to load this resource.',onRetry}:{message?:string;onRetry?:()=>void}){return <StatePanel icon={<ServerOff className="size-7"/>} title="Connection interrupted" description={message} action={onRetry?<button className="btn secondary" onClick={onRetry}><RefreshCw className="size-4"/>Retry</button>:undefined}/>}
export function ForbiddenState(){return <StatePanel icon={<LockKeyhole className="size-8 text-amber"/>} title="Access restricted" description="You do not have permission to access this resource. Backend authorization remains authoritative."/>}
export function InlineAlert({children,tone='info'}:{children:ReactNode;tone?:'info'|'danger'|'warning'|'success'}){return <div className={`alert ${tone}`} role="alert"><AlertTriangle className="size-4 shrink-0"/>{children}</div>}
