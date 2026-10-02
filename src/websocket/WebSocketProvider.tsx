import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { stompClient, type ConnectionState } from './stompClient'
const Context=createContext<ConnectionState>('DISCONNECTED')
export function WebSocketProvider({children}:{children:ReactNode}){const{token}=useAuth();const[state,setState]=useState<ConnectionState>('DISCONNECTED');useEffect(()=>stompClient.onState(setState),[]);useEffect(()=>{if(token&&!token.startsWith('demo-'))stompClient.connect(token);else if(!token)stompClient.disconnect();return()=>{if(!token)stompClient.disconnect()}},[token]);return <Context.Provider value={state}>{children}</Context.Provider>}
export const useWebSocketStatus=()=>useContext(Context)