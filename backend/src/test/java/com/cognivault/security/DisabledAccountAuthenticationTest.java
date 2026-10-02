package com.cognivault.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class DisabledAccountAuthenticationTest {
    private static final String TOKEN = "disabled-account-token";

    @Mock private JwtService jwt;
    @Mock private DatabaseUserDetailsService users;

    private UserDetails disabledUser;

    @BeforeEach
    void setUp() {
        disabledUser = User.withUsername("employee")
                .password("encoded-password")
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
                .disabled(true)
                .build();
        when(jwt.isValid(TOKEN)).thenReturn(true);
        when(jwt.username(TOKEN)).thenReturn("employee");
        when(users.loadUserByUsername("employee")).thenReturn(disabledUser);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledAccountTokenDoesNotAuthenticateHttpRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/employees");
        request.setServletPath("/api/employees");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> {
            reached.set(true);
            assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
        };

        new JwtAuthenticationFilter(jwt, users).doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertTrue(reached.get());
    }

    @Test
    void disabledAccountCannotConnectToStomp() {
        var interceptor = new StompAuthChannelInterceptor(jwt, users);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
    }
}