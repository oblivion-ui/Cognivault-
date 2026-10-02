package com.cognivault.api;
import com.cognivault.audit.AuditLedgerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/integrity")
public class IntegrityController {private final AuditLedgerService ledger;public IntegrityController(AuditLedgerService ledger){this.ledger=ledger;}
 @GetMapping("/latest") @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')") public AuditLedgerService.IntegrityResult latest(){return ledger.verify();}
 @PostMapping("/verifications") @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')") public AuditLedgerService.IntegrityResult verify(){return ledger.verify();}
}