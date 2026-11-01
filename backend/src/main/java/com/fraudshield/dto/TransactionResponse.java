package com.fraudshield.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionResponse(
        String id,
        String userId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String country,
        int score,
        String verdict,
        String status,
        long createdAt,
        List<TriggeredRuleDto> triggered
) {
}
