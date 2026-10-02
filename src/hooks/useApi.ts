import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api'
export function useApi<T>(loader:()=>Promise<T>,deps:unknown[]=[]){const [data,setData]=useState<T|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState<ApiError|null>(null);const load=useCallback(()=>{setLoading(true);setError(null);loader().then(setData).catch(e=>setError(e instanceof ApiError?e:new ApiError(0,'Unexpected application error'))).finally(()=>setLoading(false))},deps);useEffect(load,[load]);return{data,setData,loading,error,retry:load}}
