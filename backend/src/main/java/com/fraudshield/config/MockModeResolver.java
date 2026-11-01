package com.fraudshield.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class MockModeResolver {

    private static final String HEADER = "X-Mock-Response";

    private final MockProperties mockProperties;
    private final HttpServletRequest request;

    public MockModeResolver(MockProperties mockProperties, HttpServletRequest request) {
        this.mockProperties = mockProperties;
        this.request = request;
    }

    /** Resolve whether mock mode is active for the current request. */
    public boolean isMockEnabled() {
        return resolve().enabled();
    }

    /** Resolve mock mode plus its source: a per-request header overrides the config default. */
    public MockStatus resolve() {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            return new MockStatus(Boolean.parseBoolean(header.trim()), "header");
        }
        return new MockStatus(mockProperties.isEnabled(), "config");
    }

    public record MockStatus(boolean enabled, String source) {
    }
}
