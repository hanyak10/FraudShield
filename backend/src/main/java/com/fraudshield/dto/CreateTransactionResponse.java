package com.fraudshield.dto;

public record CreateTransactionResponse(
        String transactionId,
        String status,
        long createdAt
) {
}
