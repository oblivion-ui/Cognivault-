import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, configureApiAuth, ApiError } from '../api'
import { DEMO_MODE, demoUser } from '../mock/demoData'
import type { Role, User } from '../types/domain'

interface AuthValue { user: User | null; token: string | null; loading: boolean; forbidden: boolean; login: (username:string,password:string)=>Promise<User>; refresh:()=>Promise<User|null>; enterDemo: (role?: Role)=>void; logout:()=>void; clearForbidden:()=>void }
const AuthContext = createContext<AuthValue | null>(null)
const TOKEN_KEY = 'cognivault_token'

export function AuthProvider({children}:{children:ReactNode}) {
  const [token,setToken] = useState<string|null>(()=>sessionStorage.getItem(TOKEN_KEY))
  const [user,setUser] = useState<User|null>(()=>{ try { return sessionStorage.getItem('cognivault_demo')==='true' ? JSON.parse(sessionStorage.getItem('cognivault_user')||'null') : null } catch { return null } })
  const [loading,setLoading] = useState(Boolean(token))
  const [forbidden,setForbidden] = useState(false)
  const logout = useCallback(()=>{ setToken(null); setUser(null); sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem('cognivault_user'); sessionStorage.removeItem('cognivault_demo') },[])
  useEffect(()=>{ configureApiAuth({getToken:()=>token,onUnauthorized:logout,onForbidden:()=>setForbidden(true)}) },[token,logout])
  useEffect(()=>{ if(!token){setLoading(false);return} if(token.startsWith('demo-')){setLoading(false);return} setLoading(true);authApi.me().then(value=>{setUser(value);sessionStorage.setItem('cognivault_user',JSON.stringify(value))}).catch(logout).finally(()=>setLoading(false)) },[token,logout])
  const login=async(username:string,password:string)=>{ const result=await authApi.login(username,password); setToken(result.accessToken);setUser(result.user);sessionStorage.setItem(TOKEN_KEY,result.accessToken);sessionStorage.setItem('cognivault_user',JSON.stringify(result.user));return result.user }
  const refresh=useCallback(async()=>{if(!token||token.startsWith('demo-'))return user;const value=await authApi.me();setUser(value);sessionStorage.setItem('cognivault_user',JSON.stringify(value));return value},[token,user])
  const enterDemo=(role:Role='ADMIN')=>{ if(!DEMO_MODE) throw new ApiError(403,'Demo mode is disabled'); const value={...demoUser,role,name:role==='ADMIN'?'Maya Chen':`Demo ${role[0]+role.slice(1).toLowerCase()}`}; setToken('demo-session-not-a-jwt');setUser(value);sessionStorage.setItem(TOKEN_KEY,'demo-session-not-a-jwt');sessionStorage.setItem('cognivault_user',JSON.stringify(value));sessionStorage.setItem('cognivault_demo','true') }
  const value=useMemo(()=>({user,token,loading,forbidden,login,refresh,enterDemo,logout,clearForbidden:()=>setForbidden(false)}),[user,token,loading,forbidden,login,refresh,logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
export const useAuth=()=>{const value=useContext(AuthContext);if(!value)throw new Error('useAuth must be used within AuthProvider');return value}
export const isDemoSession=()=>DEMO_MODE&&sessionStorage.getItem('cognivault_demo')==='true'
