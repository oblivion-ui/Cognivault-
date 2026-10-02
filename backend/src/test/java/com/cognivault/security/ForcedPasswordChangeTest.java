package com.cognivault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
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
class ForcedPasswordChangeTest {
    private static final String TOKEN = "test-token";

    @Mock private JwtService jwt;
    @Mock private DatabaseUserDetailsService users;

    private UserDetails forcedUser;

    @BeforeEach
    void setUp() {
        forcedUser = User.withUsername("employee")
                .password("encoded-password")
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_EMPLOYEE"),
                        new SimpleGrantedAuthority(DatabaseUserDetailsService.PASSWORD_CHANGE_REQUIRED)))
                .build();
        when(jwt.isValid(TOKEN)).thenReturn(true);
        when(jwt.username(TOKEN)).thenReturn("employee");
        when(users.loadUserByUsername("employee")).thenReturn(forcedUser);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forcedPasswordChangeBlocksNormalApiRequests() throws Exception {
        var request = request("/api/employees");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void forcedPasswordChangeAllowsPasswordUpdate() throws Exception {
        var request = request("/api/auth/change-password");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void forcedPasswordChangeRejectsStompConnection() {
        var interceptor = new StompAuthChannelInterceptor(jwt, users);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwt.userId(TOKEN)).thenReturn(UUID.randomUUID());
        return request;
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwt, users);
    }
}