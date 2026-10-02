package com.cognivault.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

class AuditLedgerEventPublicationTest {

  @Test
  void persistedAuditEventIsPublishedAfterSave() {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    when(repository.findTopByOrderByChainPositionDesc()).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AuditLedgerService service =
        new AuditLedgerService(repository, new ObjectMapper(), publisher);

    var saved = service.append(
        "TEST_CREATED",
        "TEST",
        "1",
        null,
        Map.of("value", "one")
    );

    verify(repository).save(saved);

    ArgumentCaptor<Object> captor =
        ArgumentCaptor.forClass(Object.class);

    verify(publisher).publishEvent(captor.capture());

    assertInstanceOf(AuditEventCreated.class, captor.getValue());

    AuditEventCreated published =
        (AuditEventCreated) captor.getValue();

    assertSame(saved, published.event());
  }
}
