package com.cognivault.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cognivault.api.EmployeeDtos;
import com.cognivault.service.EmployeeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
@Aspect @Component
public class AuditAspect {private final AuditLedgerService ledger;private final ObjectMapper mapper;public AuditAspect(AuditLedgerService ledger,ObjectMapper mapper){this.ledger=ledger;this.mapper=mapper;}
 @Around("@annotation(action)") public Object audit(ProceedingJoinPoint p,AuditAction action)throws Throwable{
  Object before=p.getArgs().length==1?p.getArgs()[0]:java.util.Arrays.asList(p.getArgs());
  if(p.getTarget() instanceof EmployeeService employeeService&&p.getArgs().length>0&&p.getArgs()[0] instanceof java.util.UUID id){
   var employee=EmployeeDtos.EmployeeResponse.from(employeeService.get(id));
   before="CREDENTIAL_RESET".equals(action.action())?credentialState(employee):employee;
  }
  Object result=p.proceed(); Object auditAfter=result;
    if("CREDENTIAL_RESET".equals(action.action())&&result instanceof EmployeeDtos.CredentialResetResponse&&p.getArgs().length>1&&p.getArgs()[1] instanceof EmployeeDtos.CredentialResetRequest request){auditAfter=java.util.Map.of("credentialStatus",request.forceChange()?"RESET_REQUIRED":"VALID","mustChangePassword",request.forceChange());}
  String entityId=null;if(result!=null){try{var map=mapper.convertValue(result,java.util.Map.class);Object id=map.get("id");if(id==null&&map.get("employee") instanceof java.util.Map<?,?> nested)id=nested.get("id");if(id==null&&p.getArgs().length>0&&p.getArgs()[0] instanceof java.util.UUID argumentId)id=argumentId;entityId=id==null?null:String.valueOf(id);}catch(IllegalArgumentException ignored){}}
  var event=ledger.append(action.action(),action.entityType(),entityId,before,auditAfter);
  if(result instanceof EmployeeDtos.CreateEmployeeResponse created)return new EmployeeDtos.CreateEmployeeResponse(created.employee(),created.temporaryPassword(),event.getEventId());if(result instanceof EmployeeDtos.CredentialResetResponse reset)return new EmployeeDtos.CredentialResetResponse(reset.temporaryPassword(),event.getEventId());return result;
 }
 private java.util.Map<String,Object> credentialState(EmployeeDtos.EmployeeResponse employee){return java.util.Map.of("credentialStatus",employee.credentialStatus(),"mustChangePassword","RESET_REQUIRED".equals(employee.credentialStatus()));}
}
