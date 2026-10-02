package com.cognivault.service;
import com.cognivault.api.EmployeeDtos.*;
import com.cognivault.audit.AuditAction;
import com.cognivault.domain.*;
import com.cognivault.repository.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EmployeeService {
 private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";private final EmployeeRepository employees;private final UserAccountRepository users;private final PasswordEncoder encoder;private final SecureRandom random=new SecureRandom();
 public EmployeeService(EmployeeRepository employees,UserAccountRepository users,PasswordEncoder encoder){this.employees=employees;this.users=users;this.encoder=encoder;}
 @Transactional @AuditAction(action="EMPLOYEE_CREATED",entityType="EMPLOYEE") public CreateEmployeeResponse create(CreateEmployeeRequest request){if(employees.existsByEmployeeId(request.personal().employeeId()))throw new IllegalArgumentException("Employee ID already exists");if(users.existsByUsernameIgnoreCase(request.account().username()))throw new IllegalArgumentException("Username already exists");if(users.existsByEmailIgnoreCase(request.personal().email()))throw new IllegalArgumentException("Email already exists");String temporary=null;if(request.credential().method()==CredentialMethod.GENERATE)temporary=generatePassword();else{if(request.credential().password()==null||request.credential().password().length()<12)throw new IllegalArgumentException("Initial password must contain at least 12 characters");temporary=request.credential().password();}var user=new UserAccount(request.account().username(),request.personal().email(),request.personal().firstName()+" "+request.personal().lastName(),encoder.encode(temporary),request.account().role(),request.account().accountStatus(),request.credential().forceChange());users.save(user);var e=new Employee(request.personal().employeeId(),request.personal().firstName(),request.personal().lastName(),request.personal().email(),request.personal().phone(),request.personal().address(),request.employment().department(),request.employment().designation(),request.employment().manager(),request.employment().joiningDate(),request.employment().employmentType(),request.employment().location(),user);if(request.employment().manager()!=null&&!request.employment().manager().isBlank()){UserAccount managerUser=users.findByUsernameIgnoreCase(request.employment().manager()).orElseThrow(()->new IllegalArgumentException("Manager username not found"));if(managerUser.getRole()!=Role.MANAGER)throw new IllegalArgumentException("Assigned manager must have MANAGER role");e.setManagerUser(managerUser);}employees.save(e);return new CreateEmployeeResponse(EmployeeResponse.from(e),request.credential().method()==CredentialMethod.GENERATE?temporary:null,null);}
 public Page<Employee> list(Pageable pageable, Authentication authentication){ boolean managerRole=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_MANAGER")); if(!managerRole)return employees.findAllWithUser(pageable); UserAccount manager=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new IllegalArgumentException("Authenticated user not found")); return employees.findAllByManagerUserId(manager.getId(),pageable); }
 public Employee get(UUID id){return employees.findByIdWithUser(id).orElseThrow(()->new IllegalArgumentException("Employee not found"));}
 @Transactional(readOnly=true) public Employee getVisible(UUID id,Authentication authentication){Employee employee=get(id);boolean employeeRole=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_EMPLOYEE"));boolean managerRole=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_MANAGER"));if(employeeRole&&!employee.getUser().getUsername().equalsIgnoreCase(authentication.getName()))throw new AccessDeniedException("Employee scope does not include this record");if(managerRole){UserAccount manager=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new IllegalArgumentException("Authenticated user not found"));if(employee.getManagerUser()==null||!manager.getId().equals(employee.getManagerUser().getId()))throw new AccessDeniedException("Manager scope does not include this record");}return employee;} @Transactional(readOnly=true) public EmployeeResponse getVisibleResponse(UUID id,Authentication authentication){Employee employee=getVisible(id,authentication);return EmployeeResponse.from(employee);}
 public void assertVisible(UUID id,Authentication authentication){getVisible(id,authentication);}
 @Transactional @AuditAction(action="EMPLOYEE_UPDATED",entityType="EMPLOYEE") public EmployeeResponse update(UUID id,UpdateEmployeeRequest r,Authentication authentication){if(authentication==null||!authentication.isAuthenticated())throw new AccessDeniedException("Authenticated user identity is unavailable");Employee e=get(id);if(r==null)throw new IllegalArgumentException("Update request is required");if(r.role()!=null&&r.role()!=e.getUser().getRole()&&authentication.getAuthorities().stream().noneMatch(authority->authority.getAuthority().equals("ROLE_ADMIN")))throw new AccessDeniedException("Only administrators may change employee roles");e.update(r.firstName(),r.lastName(),r.email(),r.phone(),r.department(),r.designation(),r.manager(),r.employmentType(),r.location());if(r.manager()!=null&&!r.manager().isBlank()){UserAccount managerUser=users.findByUsernameIgnoreCase(r.manager()).orElseThrow(()->new IllegalArgumentException("Manager username not found"));if(managerUser.getRole()!=Role.MANAGER)throw new IllegalArgumentException("Assigned manager must have MANAGER role");e.setManagerUser(managerUser);}else{e.setManagerUser(null);}e.getUser().setEmail(r.email());e.getUser().setFullName(r.firstName()+" "+r.lastName());if(r.role()!=null)e.getUser().setRole(r.role());employees.save(e);return EmployeeResponse.from(e);}
 @Transactional
@AuditAction(action = "ACCOUNT_STATUS_CHANGED", entityType = "EMPLOYEE")
public EmployeeResponse transition(UUID id, AccountActionRequest request) {
    Employee e = get(id);

    AccountStatus status = switch (request.action().toUpperCase()) {
        case "ACTIVATE" -> AccountStatus.ACTIVE;
        case "SUSPEND" -> AccountStatus.SUSPENDED;
        case "DEACTIVATE" -> AccountStatus.DEACTIVATED;
        case "REACTIVATE" -> AccountStatus.ACTIVE;
        default -> throw new IllegalArgumentException("Unsupported account action");
    };

    e.getUser().setStatus(status);
    users.save(e.getUser());

    return EmployeeResponse.from(e);
}

@Transactional @AuditAction(action="CREDENTIAL_RESET",entityType="EMPLOYEE") public CredentialResetResponse resetCredential(UUID id,CredentialResetRequest request){Employee e=get(id);String generated=generatePassword();e.getUser().setPasswordHash(encoder.encode(generated));e.getUser().setMustChangePassword(request.forceChange());users.save(e.getUser());return new CredentialResetResponse(request.generateTemporary()?generated:null,null);}
 private String generatePassword(){StringBuilder out=new StringBuilder();for(int i=0;i<20;i++)out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));return out.toString();}
}
