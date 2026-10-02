package com.cognivault.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final UserDetailsService users;
 public JwtAuthenticationFilter(JwtService jwt,UserDetailsService users){this.jwt=jwt;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String header=request.getHeader("Authorization");if(header!=null&&header.startsWith("Bearer ")){String token=header.substring(7);try{if(jwt.isValid(token)){String username=jwt.username(token);if(SecurityContextHolder.getContext().getAuthentication()==null){var details=users.loadUserByUsername(username);if(!details.isEnabled())throw new org.springframework.security.authentication.DisabledException("Account is disabled");var principal=new AuthenticatedPrincipal(jwt.userId(token),username);var auth=new UsernamePasswordAuthenticationToken(principal,null,details.getAuthorities());auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));SecurityContextHolder.getContext().setAuthentication(auth);}}}catch(RuntimeException ignored){SecurityContextHolder.clearContext();}}var authentication=SecurityContextHolder.getContext().getAuthentication();if(authentication!=null&&authentication.getAuthorities().stream().anyMatch(authority->authority.getAuthority().equals(DatabaseUserDetailsService.PASSWORD_CHANGE_REQUIRED))&&!Set.of("/api/auth/me","/api/auth/logout","/api/auth/change-password").contains(request.getServletPath())){response.setStatus(HttpServletResponse.SC_FORBIDDEN);response.setContentType("application/json");response.getOutputStream().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Change your password to continue\"}".getBytes(StandardCharsets.UTF_8));return;}chain.doFilter(request,response);}
}