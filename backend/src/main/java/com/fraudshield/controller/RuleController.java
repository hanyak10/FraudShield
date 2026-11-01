package com.fraudshield.controller;

import com.fraudshield.dto.RuleResponse;
import com.fraudshield.dto.RuleUpdateRequest;
import com.fraudshield.service.AuditService;
import com.fraudshield.service.RuleService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rules")
public class RuleController {

    private final RuleService ruleService;
    private final AuditService auditService;

    public RuleController(RuleService ruleService, AuditService auditService) {
        this.ruleService = ruleService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<RuleResponse> list() {
        return ruleService.list();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RuleResponse update(
            @PathVariable String id,
            @Valid @RequestBody RuleUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ruleService.update(id, request, currentName(), auditService.resolveClientIp(httpRequest));
    }

    private String currentName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
