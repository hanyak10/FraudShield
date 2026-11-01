package com.fraudshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraudshield.mock")
public class MockProperties {

    /** When true, list endpoints return seeded demo data; when false, only runtime rows. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
