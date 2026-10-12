package com.plmtracker.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Jira connection settings. All values come from environment variables
 * (see application.properties). Nothing secret is hard-coded.
 */
@ConfigurationProperties(prefix = "app.jira")
public record JiraProperties(
        String baseUrl,
        String email,
        String apiToken,
        String projectKey,
        String issueType,
        Duration connectTimeout,
        Duration readTimeout
) {
    public JiraProperties {
        if (issueType == null || issueType.isBlank()) {
            issueType = "Task";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }

    /** True when URL, email and token are all filled in. */
    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(email) && notBlank(apiToken) && notBlank(projectKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Never print the token. */
    @Override
    public String toString() {
        return "JiraProperties[baseUrl=" + baseUrl + ", email=" + email
                + ", apiToken=****, projectKey=" + projectKey + ", issueType=" + issueType + "]";
    }
}
