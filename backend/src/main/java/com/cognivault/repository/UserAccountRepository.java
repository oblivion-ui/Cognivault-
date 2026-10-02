package com.cognivault.repository;
import com.cognivault.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserAccountRepository extends JpaRepository<UserAccount,UUID>{Optional<UserAccount> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);boolean existsByEmailIgnoreCase(String email);}