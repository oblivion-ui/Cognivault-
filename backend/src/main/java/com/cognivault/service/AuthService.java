package com.cognivault.service;
import com.cognivault.api.AuthDtos.*;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.UserAccountRepository;
import com.cognivault.security.JwtService;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
 private final AuthenticationManager auth;private final UserAccountRepository users;private final JwtService jwt;private final PasswordEncoder encoder;
 public AuthService(AuthenticationManager auth,UserAccountRepository users,JwtService jwt,PasswordEncoder encoder){this.auth=auth;this.users=users;this.jwt=jwt;this.encoder=encoder;}
 @Transactional public LoginResponse login(LoginRequest request){auth.authenticate(new UsernamePasswordAuthenticationToken(request.username(),request.password()));UserAccount user=users.findByUsernameIgnoreCase(request.username()).orElseThrow();user.setLastLogin(Instant.now());users.save(user);return new LoginResponse(jwt.issue(user),null,3600,UserResponse.from(user));}
 @Transactional public UserResponse changePassword(String username,ChangePasswordRequest request){if(request.newPassword()==null||request.newPassword().length()<12)throw new IllegalArgumentException("New password must contain at least 12 characters");UserAccount user=users.findByUsernameIgnoreCase(username).orElseThrow();if(!encoder.matches(request.currentPassword(),user.getPasswordHash()))throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");user.setPasswordHash(encoder.encode(request.newPassword()));user.setMustChangePassword(false);users.save(user);return UserResponse.from(user);}
 public UserResponse me(String username){return UserResponse.from(users.findByUsernameIgnoreCase(username).orElseThrow());}
}