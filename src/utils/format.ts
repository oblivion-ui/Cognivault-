export const formatDateTime=(value?:string)=>value?new Intl.DateTimeFormat(undefined,{dateStyle:'medium',timeStyle:'short'}).format(new Date(value)):'Not available'
export const formatDate=(value?:string)=>value?new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(value)):'Not available'
export const initials=(name?:string)=>name?.split(' ').map(p=>p[0]).slice(0,2).join('').toUpperCase()||'CV'
export const titleCase=(value:string)=>value.replace(/_/g,' ').toLowerCase().replace(/\b\w/g,(c:string)=>c.toUpperCase())
