package com.plmtracker.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The small part of Jira's "issue updated" webhook we care about.
 * Everything else in the (large) payload is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraWebhookPayload(String webhookEvent, Issue issue) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(String key, Fields fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fields(Status status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String name, StatusCategory statusCategory) {
    }

    /** Jira groups every status into a category: "new", "indeterminate" or "done". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusCategory(String key) {
    }

    /** The Jira issue key (for example KAN-12), or null if the payload has none. */
    public String issueKey() {
        return issue == null ? null : issue.key();
    }

    /** The status category key, or null if the payload has none. */
    public String statusCategoryKey() {
        if (issue == null || issue.fields() == null || issue.fields().status() == null
                || issue.fields().status().statusCategory() == null) {
            return null;
        }
        return issue.fields().status().statusCategory().key();
    }
}
