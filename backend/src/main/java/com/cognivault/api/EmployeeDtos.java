package com.cognivault.api;
import com.cognivault.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
public final class EmployeeDtos {private EmployeeDtos(){}
 public enum CredentialMethod{SET,GENERATE}
 public record CreateEmployeeRequest(@Valid Personal personal,@Valid Employment employment,@Valid Account account,@Valid Credential credential){}
 public record Personal(@NotBlank String firstName,@NotBlank String lastName,@NotBlank String employeeId,@Email @NotBlank String email,String phone,String address){}
 public record Employment(@NotBlank String department,@NotBlank String designation,String manager,@NotNull LocalDate joiningDate,String employmentType,String location){}
 public record Account(@NotBlank String username,@NotNull Role role,@NotNull AccountStatus accountStatus){}
 public record Credential(@NotNull CredentialMethod method,String password,boolean forceChange){}
 public record CredentialResetRequest(boolean generateTemporary,boolean forceChange){}
 public record UpdateEmployeeRequest(@NotBlank String firstName,@NotBlank String lastName,@Email @NotBlank String email,String phone,String department,String designation,String manager,String employmentType,String location,Role role){}
 public record AccountActionRequest(@NotBlank String action,@NotBlank String reason){}
 public record EmployeeResponse(UUID id,String employeeId,String firstName,String lastName,String email,String phone,String address,String department,String designation,String manager,LocalDate joiningDate,String employmentType,String location,String username,Role role,AccountStatus accountStatus,String credentialStatus,Instant lastLogin,Instant createdAt){public static EmployeeResponse from(Employee e){var u=e.getUser();return new EmployeeResponse(e.getId(),e.getEmployeeId(),e.getFirstName(),e.getLastName(),e.getEmail(),e.getPhone(),e.getAddress(),e.getDepartment(),e.getDesignation(),e.getManager(),e.getJoiningDate(),e.getEmploymentType(),e.getLocation(),u.getUsername(),u.getRole(),u.getStatus(),u.isMustChangePassword()?"RESET_REQUIRED":"VALID",u.getLastLogin(),u.getCreatedAt());}}
  public record CreateEmployeeResponse(EmployeeResponse employee,String temporaryPassword,String auditEventId){}
  public record CredentialResetResponse(String temporaryPassword,String auditEventId){}
}