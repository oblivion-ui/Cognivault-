import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { FullPageLoader } from '../components/common/States'
export function ProtectedRoute(){const {user,loading}=useAuth();const location=useLocation();if(loading)return <FullPageLoader/>;if(!user)return <Navigate to="/login" replace state={{from:location,reason:'session-required'}}/>;if(user.mustChangePassword&&location.pathname!='/force-password-change')return <Navigate to="/force-password-change" replace/>;return <Outlet/>}
