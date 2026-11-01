package com.fraudshield.controller;

import com.fraudshield.dto.CreateTransactionRequest;
import com.fraudshield.dto.CreateTransactionResponse;
import com.fraudshield.dto.TransactionResponse;
import com.fraudshield.service.AuditService;
import com.fraudshield.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AuditService auditService;

    public TransactionController(TransactionService transactionService, AuditService auditService) {
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<TransactionResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String status) {
        return transactionService.list(q, verdict, status);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return transactionService.get(id);
    }

    @PostMapping
    public ResponseEntity<CreateTransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            HttpServletRequest httpRequest) {
        String userId = currentUserId();
        CreateTransactionResponse response =
                transactionService.create(request, userId, auditService.resolveClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
