import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'
import type { Role } from '../types/domain'
import { ForbiddenState } from '../components/common/States'
export function RoleGuard({allow,children}:{allow:Role[];children:ReactNode}){const {user}=useAuth();return user&&allow.includes(user.role)?children:<ForbiddenState/>}