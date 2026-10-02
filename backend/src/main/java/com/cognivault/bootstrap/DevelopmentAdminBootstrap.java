package com.cognivault.bootstrap;

import com.cognivault.config.CogniVaultProperties;
import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.UserAccountRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevelopmentAdminBootstrap {

    private final CogniVaultProperties props;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public DevelopmentAdminBootstrap(
            CogniVaultProperties props,
            UserAccountRepository users,
            PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        var bootstrap = props.bootstrap();

        if (bootstrap.enabled()) {
            createUserIfConfigured(
                    bootstrap.username(),
                    bootstrap.email(),
                    bootstrap.password(),
                    bootstrap.name(),
                    Role.ADMIN,
                    "Development bootstrap requires username, email, and a password of at least 12 characters");
        }

        if (bootstrap.secondAdminEnabled()) {
            createUserIfConfigured(
                    bootstrap.secondAdminUsername(),
                    bootstrap.secondAdminEmail(),
                    bootstrap.secondAdminPassword(),
                    bootstrap.secondAdminName(),
                    Role.ADMIN,
                    "Second development admin bootstrap requires username, email, and a password of at least 12 characters");
        }

        if (bootstrap.hrEnabled()) {
            createUserIfConfigured(
                    bootstrap.hrUsername(),
                    bootstrap.hrEmail(),
                    bootstrap.hrPassword(),
                    bootstrap.hrName(),
                    Role.HR,
                    "HR development bootstrap requires username, email, and a password of at least 12 characters");

            if (bootstrap.hrResetPassword()) {
                resetExistingUserPassword(
                        bootstrap.hrUsername(),
                        bootstrap.hrPassword());
            }
        }
    }

    private void resetExistingUserPassword(String username, String password) {
        var user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Configured HR development user does not exist: " + username));

        user.setPasswordHash(encoder.encode(password));
        user.setMustChangePassword(false);
        users.save(user);
    }
    private void createUserIfConfigured(
            String username,
            String email,
            String password,
            String name,
            Role role,
            String validationMessage) {

        if (username == null
                || username.isBlank()
                || email == null
                || email.isBlank()
                || password == null
                || password.length() < 12) {
            throw new IllegalStateException(validationMessage);
        }

        if (users.existsByUsernameIgnoreCase(username)
                || users.existsByEmailIgnoreCase(email)) {
            return;
        }

        users.save(new UserAccount(
                username,
                email,
                name,
                encoder.encode(password),
                role,
                AccountStatus.ACTIVE,
                false));
    }
}
