package com.fraudshield.controller;

import com.fraudshield.config.MockModeResolver;
import com.fraudshield.config.MockModeResolver.MockStatus;
import com.fraudshield.dto.MockStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final MockModeResolver mockModeResolver;

    public ConfigController(MockModeResolver mockModeResolver) {
        this.mockModeResolver = mockModeResolver;
    }

    @GetMapping("/mock-status")
    public MockStatusResponse mockStatus() {
        MockStatus status = mockModeResolver.resolve();
        return new MockStatusResponse(status.enabled(), status.source());
    }
}
