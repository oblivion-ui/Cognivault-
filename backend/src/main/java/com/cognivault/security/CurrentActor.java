package com.cognivault.security;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
public final class CurrentActor {private CurrentActor(){}public static Actor get(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())return new Actor("SYSTEM",null);Object principal=a.getPrincipal();String username=a.getName();String id=principal instanceof AuthenticatedPrincipal p?p.userId().toString():null;return new Actor(username,id);}public record Actor(String username,String userId){}public interface AuthenticatedPrincipal{UUID userId();}}