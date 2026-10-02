package com.cognivault.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditLedgerServiceTest {
  @Test void validChainPassesVerification(){
    AuditEventRepository repository=mock(AuditEventRepository.class);
    when(repository.findTopByOrderByChainPositionDesc()).thenReturn(Optional.empty());
    AuditLedgerService service=new AuditLedgerService(repository,new ObjectMapper());
    when(repository.save(any())).thenAnswer(invocation->invocation.getArgument(0));
    service.append("TEST_CREATED","TEST","1",null,Map.of("value","one"));
    var captor=org.mockito.ArgumentCaptor.forClass(AuditEventDocument.class);
    verify(repository).save(captor.capture());
    when(repository.ordered()).thenReturn(List.of(captor.getValue()));
    var result=service.verify();
    assertEquals("VERIFIED",result.status());
  }

  @Test void tamperedHashFailsVerification(){
    AuditEventRepository repository=mock(AuditEventRepository.class);
    AuditLedgerService service=new AuditLedgerService(repository,new ObjectMapper());
    AuditEventDocument event=new AuditEventDocument("evt-1","tester",null,"TEST_CREATED","TEST","1",Instant.parse("2026-01-01T00:00:00Z"),null,new TreeMap<>(Map.of("value","one")),"GENESIS","bad",1);
    event.getAfterSnapshot().put("value","tampered");
    when(repository.ordered()).thenReturn(List.of(event));
    var result=service.verify();
    assertEquals("FAILED",result.status());
    assertEquals("evt-1",result.affectedEventId());
  }

  @Test void subsequentEventLinksToPreviousHash(){
    AuditEventRepository repository=mock(AuditEventRepository.class);
    when(repository.findTopByOrderByChainPositionDesc()).thenReturn(Optional.empty());
    AuditLedgerService service=new AuditLedgerService(repository,new ObjectMapper());
    when(repository.save(any())).thenAnswer(invocation->invocation.getArgument(0));
    var first=service.append("FIRST","TEST","1",null,Map.of("value","one"));
    when(repository.findTopByOrderByChainPositionDesc()).thenReturn(Optional.of(first));
    var second=service.append("SECOND","TEST","1",Map.of("value","one"),Map.of("value","two"));
    assertEquals(1,first.getChainPosition());
    assertEquals(2,second.getChainPosition());
    assertEquals(first.getCurrentHash(),second.getPreviousHash());
  }

  @Test void safeCredentialStateRemainsWhileCredentialValuesAreRemoved(){
    AuditEventRepository repository=mock(AuditEventRepository.class);
    when(repository.findTopByOrderByChainPositionDesc()).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation->invocation.getArgument(0));
    AuditLedgerService service=new AuditLedgerService(repository,new ObjectMapper());
    service.append("CREDENTIAL_RESET","EMPLOYEE","1",Map.of("mustChangePassword",false,"credentialStatus","VALID","passwordHash","hash"),Map.of("mustChangePassword",true,"credentialStatus","RESET_REQUIRED","temporaryPassword","secret","accessToken","token"));
    var captor=org.mockito.ArgumentCaptor.forClass(AuditEventDocument.class);
    verify(repository).save(captor.capture());
    assertEquals(Map.of("mustChangePassword",false,"credentialStatus","VALID"),captor.getValue().getBeforeSnapshot());
    assertEquals(Map.of("mustChangePassword",true,"credentialStatus","RESET_REQUIRED"),captor.getValue().getAfterSnapshot());
  }
}