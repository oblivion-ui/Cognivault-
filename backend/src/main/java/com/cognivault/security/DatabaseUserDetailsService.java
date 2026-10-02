package com.cognivault.security;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
@Service
public class DatabaseUserDetailsService implements UserDetailsService {
 public static final String PASSWORD_CHANGE_REQUIRED="PASSWORD_CHANGE_REQUIRED";
 private final UserAccountRepository repository;
 public DatabaseUserDetailsService(UserAccountRepository repository){this.repository=repository;}
 @Override public UserDetails loadUserByUsername(String username)throws UsernameNotFoundException{UserAccount u=repository.findByUsernameIgnoreCase(username).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));var builder=User.withUsername(u.getUsername()).password(u.getPasswordHash()).disabled(!u.isEnabled()||u.getStatus().name().equals("DEACTIVATED")||u.getStatus().name().equals("SUSPENDED"));var authorities=new java.util.ArrayList<SimpleGrantedAuthority>();authorities.add(new SimpleGrantedAuthority("ROLE_"+u.getRole().name()));if(u.isMustChangePassword())authorities.add(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED));return builder.authorities(authorities).build();}
}