package com.cognivault.config;
import com.cognivault.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import java.nio.charset.StandardCharsets;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 private final JwtAuthenticationFilter jwtFilter;
 public SecurityConfig(JwtAuthenticationFilter jwtFilter){this.jwtFilter=jwtFilter;}
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config)throws Exception{return config.getAuthenticationManager();}
 @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.csrf(csrf->csrf.disable()).cors(cors->cors.configurationSource(corsConfigurationSource())).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint(jsonEntryPoint()).accessDeniedHandler(jsonDeniedHandler())).authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/health/**","/actuator/health","/ws/**").permitAll().anyRequest().authenticated()).addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class).build();}
 @Bean AuthenticationEntryPoint jsonEntryPoint(){return (request,response,exception)->{response.setStatus(401);response.setContentType("application/json");response.getOutputStream().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}".getBytes(StandardCharsets.UTF_8));};}
 @Bean AccessDeniedHandler jsonDeniedHandler(){return (request,response,exception)->{response.setStatus(403);response.setContentType("application/json");response.getOutputStream().write("{\"code\":\"FORBIDDEN\",\"message\":\"You do not have permission to perform this action.\"}".getBytes(StandardCharsets.UTF_8));};}
 @Bean CorsConfigurationSource corsConfigurationSource(){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of("http://localhost:5173","http://localhost:4173"));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
