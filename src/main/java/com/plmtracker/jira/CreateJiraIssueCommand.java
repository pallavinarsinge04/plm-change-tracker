package com.plmtracker.jira;

/** Everything the Jira client needs to build an issue. Plain data, no Jira details. */
public record CreateJiraIssueCommand(
        String partNumber,
        String title,
        String reason,
        String priority,
        String requestedBy
) {
}
