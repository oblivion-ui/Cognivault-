package com.cognivault.api;
import com.cognivault.domain.*;
import java.time.Instant;
import java.util.UUID;
public final class AuthDtos {private AuthDtos(){}
 public record LoginRequest(String username,String password){}
 public record LoginResponse(String accessToken,String refreshToken,long expiresIn,UserResponse user){}
 public record UserResponse(UUID id,String username,String name,String email,Role role,String department,AccountStatus accountStatus,Instant lastLogin,boolean mustChangePassword){public static UserResponse from(com.cognivault.domain.UserAccount u){return new UserResponse(u.getId(),u.getUsername(),u.getFullName(),u.getEmail(),u.getRole(),null,u.getStatus(),u.getLastLogin(),u.isMustChangePassword());}}
 public record ChangePasswordRequest(String currentPassword,String newPassword){}
}