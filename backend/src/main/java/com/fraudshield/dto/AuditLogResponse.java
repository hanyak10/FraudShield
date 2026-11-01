package com.fraudshield.dto;

public record AuditLogResponse(
        Long id,
        String actor,
        String action,
        String ip,
        String ico,
        long at
) {
}
