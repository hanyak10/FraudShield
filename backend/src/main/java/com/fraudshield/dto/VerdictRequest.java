package com.fraudshield.dto;

import jakarta.validation.constraints.NotBlank;

public record VerdictRequest(
        @NotBlank String decision
) {
}
