package com.fraudshield.service;

import com.fraudshield.config.MockModeResolver;
import com.fraudshield.model.AuditLog;
import com.fraudshield.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final MockModeResolver mockModeResolver;

    public AuditService(AuditLogRepository auditLogRepository, MockModeResolver mockModeResolver) {
        this.auditLogRepository = auditLogRepository;
        this.mockModeResolver = mockModeResolver;
    }

    public void log(String actor, String action, String ip, String icon) {
        auditLogRepository.save(new AuditLog(actor, action, ip, icon, Instant.now(), false));
    }

    public List<AuditLog> recent() {
        // In real mode (mock off) hide seeded demo entries; in mock mode return everything.
        return mockModeResolver.isMockEnabled()
                ? auditLogRepository.findAllByOrderByCreatedAtDesc()
                : auditLogRepository.findBySeededFalseOrderByCreatedAtDesc();
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
