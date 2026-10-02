package com.cognivault.api;
import com.cognivault.audit.*;
import com.cognivault.service.EmployeeScopeService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/audit-events")
public class AuditController {
	private final AuditEventRepository repository;
	private final EmployeeScopeService employeeScope;

	public AuditController(AuditEventRepository repository, EmployeeScopeService employeeScope) {
		this.repository = repository;
		this.employeeScope = employeeScope;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER')")
	public Page<AuditDtos.AuditResponse> list(Pageable pageable, Authentication authentication) {
		if (!employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER)) {
			return repository.findAll(pageable).map(AuditDtos.AuditResponse::from);
		}
		List<String> employeeIds = employeeScope.managedEmployeeIds(authentication).stream().sorted().toList();
		if (employeeIds.isEmpty()) {
			return Page.empty(pageable);
		}
		return repository.findByEntityTypeAndEntityIdInOrderByChainPositionAsc(
						"EMPLOYEE", employeeIds, pageable)
				.map(AuditDtos.AuditResponse::from);
	}

	@GetMapping("/{eventId}")
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER')")
	public AuditDtos.AuditResponse get(@PathVariable String eventId, Authentication authentication) {
		var event = repository.findByEventId(eventId)
				.orElseThrow(() -> new IllegalArgumentException("Audit event not found"));
		assertManagerEventVisible(event, authentication);
		return AuditDtos.AuditResponse.from(event);
	}

	@GetMapping("/employee/{employeeId}")
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER')")
	public Page<AuditDtos.AuditResponse> employee(
			@PathVariable String employeeId,
			Pageable pageable,
			Authentication authentication) {
		if (employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER)) {
			employeeScope.assertManagedBy(UUID.fromString(employeeId), authentication);
		}
		return repository.findByEntityTypeAndEntityId("EMPLOYEE", employeeId, pageable)
				.map(AuditDtos.AuditResponse::from);
	}

	private void assertManagerEventVisible(
			AuditEventDocument event,
			Authentication authentication) {
		if (!employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER)) {
			return;
		}
		if (!"EMPLOYEE".equalsIgnoreCase(event.getEntityType())
				|| event.getEntityId() == null || event.getEntityId().isBlank()) {
			throw new AccessDeniedException("Manager scope cannot be established for this audit event");
		}
		try {
			employeeScope.assertManagedBy(UUID.fromString(event.getEntityId()), authentication);
		} catch (IllegalArgumentException exception) {
			throw new AccessDeniedException("Manager scope cannot be established for this audit event");
		}
	}
}
