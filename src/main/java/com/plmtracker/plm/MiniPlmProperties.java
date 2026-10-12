package com.plmtracker.plm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How to reach the Mini PLM service (project 1). The tracker logs in with a
 * service account. If baseUrl is empty, part validation is switched off.
 */
@ConfigurationProperties(prefix = "app.miniplm")
public record MiniPlmProperties(
        String baseUrl,
        String email,
        String password,
        Duration connectTimeout,
        Duration readTimeout
) {
    public MiniPlmProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(email) && notBlank(password);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "MiniPlmProperties[baseUrl=" + baseUrl + ", email=" + email + ", password=****]";
    }
}
