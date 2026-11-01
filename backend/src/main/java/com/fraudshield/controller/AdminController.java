package com.fraudshield.controller;

import com.fraudshield.dto.AuditLogResponse;
import com.fraudshield.dto.TransactionResponse;
import com.fraudshield.dto.VerdictRequest;
import com.fraudshield.model.AuditLog;
import com.fraudshield.service.AuditService;
import com.fraudshield.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TransactionService transactionService;
    private final AuditService auditService;

    public AdminController(TransactionService transactionService, AuditService auditService) {
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> transactions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String status) {
        return transactionService.list(q, verdict, status);
    }

    @PutMapping("/verdicts/{id}")
    public TransactionResponse applyVerdict(
            @PathVariable String id,
            @Valid @RequestBody VerdictRequest request,
            HttpServletRequest httpRequest) {
        return transactionService.applyVerdict(
                id, request.decision(), currentName(), auditService.resolveClientIp(httpRequest));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<AuditLogResponse> auditLogs() {
        return auditService.recent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor(),
                log.getAction(),
                log.getIp(),
                log.getIcon(),
                log.getCreatedAt().toEpochMilli()
        );
    }

    private String currentName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
