import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Activity, Bell, BookOpenCheck, Bot, ChevronLeft, ChevronRight, ClipboardCheck, DatabaseZap, FileBarChart, Fingerprint, Gauge, GitBranch, HeartPulse, LayoutDashboard, LogOut, Menu, Network, Search, Settings, ShieldAlert, ShieldCheck, UserCircle, UserRoundCog, Users, X } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import type { Role } from '../../types/domain'
import { initials } from '../../utils/format'
import { useWebSocketStatus } from '../../websocket/WebSocketProvider'

type NavItem={label:string;to:string;icon:typeof Gauge;roles:Role[]}
const all:Role[]=['ADMIN','HR','MANAGER','AUDITOR','COMPLIANCE_OFFICER','EMPLOYEE']
const navGroups:Array<{label:string;items:NavItem[]}>= [
  {label:'Overview',items:[{label:'Dashboard',to:'/dashboard',icon:LayoutDashboard,roles:all}]},
  {label:'Workforce',items:[{label:'Employees',to:'/employees',icon:Users,roles:['ADMIN','HR','MANAGER']},{label:'Employee lifecycle',to:'/lifecycle',icon:UserRoundCog,roles:['ADMIN','HR','MANAGER']}]},
  {label:'Audit & assurance',items:[{label:'Audit explorer',to:'/audit',icon:Search,roles:['ADMIN','AUDITOR','MANAGER']},{label:'Chain integrity',to:'/integrity',icon:Fingerprint,roles:['ADMIN','AUDITOR']},{label:'Investigation',to:'/investigator',icon:Bot,roles:['ADMIN','AUDITOR','COMPLIANCE_OFFICER']}]},
  {label:'Risk & intelligence',items:[{label:'Risk & anomalies',to:'/anomalies',icon:ShieldAlert,roles:['ADMIN','AUDITOR','MANAGER']},{label:'Predictive analytics',to:'/predictive',icon:Activity,roles:['ADMIN','AUDITOR']}]},
  {label:'Governance',items:[{label:'Approvals',to:'/approvals',icon:ClipboardCheck,roles:['ADMIN','HR','MANAGER']},{label:'Remediation',to:'/remediation',icon:BookOpenCheck,roles:['ADMIN','AUDITOR']},{label:'Rollback',to:'/rollback',icon:DatabaseZap,roles:['ADMIN']},{label:'Data lineage',to:'/lineage',icon:GitBranch,roles:['ADMIN','AUDITOR']}]},
  {label:'Reporting',items:[{label:'Reports',to:'/reports',icon:FileBarChart,roles:['ADMIN','AUDITOR','MANAGER']}]},
  {label:'Self-service',items:[{label:'My requests',to:'/employee-requests',icon:ClipboardCheck,roles:['EMPLOYEE']}]},{label:'System',items:[{label:'Service health',to:'/service-health',icon:HeartPulse,roles:['ADMIN']},{label:'Notifications',to:'/notifications',icon:Bell,roles:all},{label:'Administration',to:'/admin',icon:Settings,roles:['ADMIN']}]},
]

export function AppShell(){const{user,logout}=useAuth();const navigate=useNavigate();const location=useLocation();const[collapsed,setCollapsed]=useState(false);const[mobileOpen,setMobileOpen]=useState(false);const ws=useWebSocketStatus();const demo=sessionStorage.getItem('cognivault_demo')==='true';
  const doLogout=()=>{logout();navigate('/login')};
  return <div className={collapsed?'app-frame collapsed':'app-frame'}>
    <aside className={mobileOpen?'sidebar mobile-open':'sidebar'}>
      <div className="brand"><div className="brand-mark"><ShieldCheck/></div><div className="brand-copy"><strong>CogniVault</strong><span>Audit intelligence</span></div><button className="mobile-close" onClick={()=>setMobileOpen(false)} aria-label="Close navigation"><X/></button></div>
      {demo&&<div className="demo-label"><span/>Demo data workspace</div>}
      <nav aria-label="Primary navigation">{navGroups.map(group=>{const items=group.items.filter(i=>user&&i.roles.includes(user.role));if(!items.length)return null;return <div className="nav-group" key={group.label}><p>{group.label}</p>{items.map(({label,to,icon:Icon})=><NavLink key={to} to={to} onClick={()=>setMobileOpen(false)} className={({isActive})=>isActive?'active':''}><Icon/><span>{label}</span></NavLink>)}</div>})}</nav>
      <div className="sidebar-foot"><NavLink to="/profile" className={location.pathname==='/profile'?'user-tile active':'user-tile'}><div className="avatar">{initials(user?.name)}</div><div><strong>{user?.name}</strong><span>{user?.role}</span></div></NavLink><button className="logout" onClick={doLogout} aria-label="Log out"><LogOut/></button></div>
      <button className="collapse" onClick={()=>setCollapsed(v=>!v)} aria-label="Toggle sidebar">{collapsed?<ChevronRight/>:<ChevronLeft/>}</button>
    </aside>
    {mobileOpen&&<button className="mobile-overlay" onClick={()=>setMobileOpen(false)} aria-label="Close navigation overlay"/>}
    <div className="main-area"><header className="topbar"><button className="menu-btn" onClick={()=>setMobileOpen(true)} aria-label="Open navigation"><Menu/></button><div className="top-context"><Network/><span>Enterprise workspace</span><i/></div><div className="top-actions"><div className={`connection ${ws.toLowerCase()}`}><span/>{ws==='CONNECTED'?'STOMP connected':ws==='CONNECTING'?'Connecting':ws==='NOT_CONFIGURED'?'Realtime not configured':'Realtime unavailable'}</div><NavLink to="/notifications" aria-label="Notifications"><Bell/></NavLink><NavLink to="/profile" aria-label="Profile"><UserCircle/></NavLink></div></header><main><Outlet/></main></div>
  </div>
}
