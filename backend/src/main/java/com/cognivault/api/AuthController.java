package com.cognivault.api;
import com.cognivault.api.AuthDtos.*;
import com.cognivault.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {private final AuthService service;public AuthController(AuthService service){this.service=service;}
 @PostMapping("/login") public LoginResponse login(@RequestBody @Valid LoginRequest request){if(request.username()==null||request.password()==null)throw new IllegalArgumentException("Username and password are required");return service.login(request);}
 @GetMapping("/me") public UserResponse me(Authentication authentication){return service.me(authentication.getName());}
 @PostMapping("/logout") public ResponseEntity<Void> logout(){return ResponseEntity.noContent().build();}
 @PostMapping("/change-password") public UserResponse change(Authentication authentication,@RequestBody @Valid ChangePasswordRequest request){return service.changePassword(authentication.getName(),request);}
}