package com.cognivault.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="users",uniqueConstraints={@UniqueConstraint(name="uk_users_username",columnNames="username"),@UniqueConstraint(name="uk_users_email",columnNames="email")})
public class UserAccount {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=80) private String username;
 @Column(nullable=false,length=254) private String email;
 @Column(name="full_name",nullable=false,length=160) private String fullName;
 @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AccountStatus status;
 @Column(name="must_change_password",nullable=false) private boolean mustChangePassword;
 @Column(nullable=false) private boolean enabled=true;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="last_login") private Instant lastLogin;
 protected UserAccount(){}
 public UserAccount(String username,String email,String fullName,String passwordHash,Role role,AccountStatus status,boolean mustChangePassword){this.username=username;this.email=email;this.fullName=fullName;this.passwordHash=passwordHash;this.role=role;this.status=status;this.mustChangePassword=mustChangePassword;this.createdAt=Instant.now();this.enabled=status==AccountStatus.ACTIVE||status==AccountStatus.PENDING;}
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public String getUsername(){return username;} public String getEmail(){return email;} public String getFullName(){return fullName;} public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;} public AccountStatus getStatus(){return status;} public boolean isMustChangePassword(){return mustChangePassword;} public boolean isEnabled(){return enabled;} public Instant getCreatedAt(){return createdAt;} public Instant getLastLogin(){return lastLogin;}
 public void setPasswordHash(String v){passwordHash=v;} public void setMustChangePassword(boolean v){mustChangePassword=v;} public void setStatus(AccountStatus v){status=v;enabled=v==AccountStatus.ACTIVE||v==AccountStatus.PENDING;} public void setLastLogin(Instant v){lastLogin=v;} public void setRole(Role v){role=v;} public void setFullName(String v){fullName=v;} public void setEmail(String v){email=v;}
}