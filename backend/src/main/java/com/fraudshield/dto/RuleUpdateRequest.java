package com.fraudshield.dto;

public record RuleUpdateRequest(
        Boolean enabled,
        Integer weight
) {
}
