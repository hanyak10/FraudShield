package com.fraudshield.dto;

public record RuleResponse(
        String id,
        String name,
        String desc,
        String description,
        int weight,
        boolean enabled
) {
}
