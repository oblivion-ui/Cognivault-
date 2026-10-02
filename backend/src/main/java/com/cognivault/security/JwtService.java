package com.cognivault.security;

import com.cognivault.config.CogniVaultProperties;
import com.cognivault.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final CogniVaultProperties properties;
    private SecretKey key;
    public JwtService(CogniVaultProperties properties){this.properties=properties;}
    @PostConstruct void initialize(){String secret=properties.jwt().secret();if(secret==null||secret.length()<32)throw new IllegalStateException("COGNIVAULT_JWT_SECRET must be set and contain at least 32 characters");key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    public String issue(UserAccount user){Instant now=Instant.now();return Jwts.builder().subject(user.getUsername()).claim("uid",user.getId().toString()).claim("role",user.getRole().name()).claim("mustChangePassword",user.isMustChangePassword()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(properties.jwt().expirationMinutes()*60))).signWith(key).compact();}
    public Claims parse(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
    public boolean isValid(String token){try{parse(token);return true;}catch(RuntimeException e){return false;}}
    public String username(String token){return parse(token).getSubject();}
    public UUID userId(String token){return UUID.fromString(parse(token).get("uid",String.class));}
}