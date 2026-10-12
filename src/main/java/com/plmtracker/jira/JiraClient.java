package com.plmtracker.jira;

/**
 * Port to Jira. Services depend on this interface, so tests can use a fake
 * and never call the real Jira.
 */
public interface JiraClient {

    /**
     * Creates a Jira issue.
     *
     * @throws JiraIntegrationException if Jira is not configured, unreachable, or rejects the request
     */
    JiraIssue createIssue(CreateJiraIssueCommand command);
}
