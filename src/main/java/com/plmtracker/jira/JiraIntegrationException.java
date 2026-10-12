package com.plmtracker.jira;

/** Something went wrong while talking to Jira. */
public class JiraIntegrationException extends RuntimeException {

    /** HTTP status returned by Jira, or null if Jira could not be reached at all. */
    private final Integer upstreamStatus;

    public JiraIntegrationException(String message, Integer upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public JiraIntegrationException(String message, Integer upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }
}
