package com.fraudshield.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
) {
}
