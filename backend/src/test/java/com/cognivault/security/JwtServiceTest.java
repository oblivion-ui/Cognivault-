package com.cognivault.security;

import static org.junit.jupiter.api.Assertions.*;
import com.cognivault.config.CogniVaultProperties;
import com.cognivault.domain.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
  @Test void issuedTokenCarriesIdentityAndRole(){
    String secret="local-test-secret-that-is-at-least-32-characters";
    var properties=new CogniVaultProperties(new CogniVaultProperties.Jwt(secret,60),new CogniVaultProperties.Bootstrap(false,"","","","",false,"","","","Four-Eyes Approver",false,false,"","","",""),new CogniVaultProperties.Ml("",""),new CogniVaultProperties.Websocket(""));
    var service=new JwtService(properties);service.initialize();
    var user=new UserAccount("admin.local","admin@example.test","Admin","$2a$10$not-used",Role.ADMIN,AccountStatus.ACTIVE,false);
    UUID id=UUID.randomUUID();ReflectionTestUtils.setField(user,"id",id);
    String token=service.issue(user);
    assertEquals("admin.local",service.username(token));
    assertEquals(id,service.userId(token));
    assertTrue(service.isValid(token));
  }
}
