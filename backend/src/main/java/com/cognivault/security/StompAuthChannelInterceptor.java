package com.cognivault.security;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
 private final JwtService jwt;private final DatabaseUserDetailsService users;public StompAuthChannelInterceptor(JwtService jwt,DatabaseUserDetailsService users){this.jwt=jwt;this.users=users;}
 @Override public Message<?> preSend(Message<?> message,MessageChannel channel){StompHeaderAccessor accessor=StompHeaderAccessor.wrap(message);if(StompCommand.CONNECT.equals(accessor.getCommand())){String header=accessor.getFirstNativeHeader("Authorization");if(header==null||!header.startsWith("Bearer ")||!jwt.isValid(header.substring(7)))throw new IllegalArgumentException("STOMP authentication required");String token=header.substring(7);String username=jwt.username(token);var details=users.loadUserByUsername(username);if(!details.isEnabled())throw new IllegalArgumentException("Account is disabled");if(details.getAuthorities().stream().anyMatch(authority->authority.getAuthority().equals(DatabaseUserDetailsService.PASSWORD_CHANGE_REQUIRED)))throw new IllegalArgumentException("Password change required before STOMP connection");var principal=new AuthenticatedPrincipal(jwt.userId(token),username);accessor.setUser(new UsernamePasswordAuthenticationToken(principal,null,details.getAuthorities()));}return message;}
}