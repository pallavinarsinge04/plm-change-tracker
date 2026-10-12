package com.plmtracker.jira;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Parses a trimmed-down but realistic Jira Cloud "issue updated" payload. */
class JiraWebhookPayloadTest {

    private static final String JIRA_JSON = """
            {
              "timestamp": 1760000000000,
              "webhookEvent": "jira:issue_updated",
              "user": { "displayName": "Pallavi", "accountId": "abc" },
              "issue": {
                "id": "10006",
                "key": "KAN-12",
                "self": "https://example.atlassian.net/rest/api/2/issue/10006",
                "fields": {
                  "summary": "[P-3001] Use stronger bolt",
                  "status": {
                    "name": "In Progress",
                    "statusCategory": { "id": 4, "key": "indeterminate", "name": "In Progress" }
                  },
                  "labels": []
                }
              },
              "changelog": { "items": [ { "field": "status", "fromString": "To Do", "toString": "In Progress" } ] }
            }
            """;

    @Test
    void parsesKeyAndStatusCategory_andIgnoresEverythingElse() {
        JiraWebhookPayload payload = JsonMapper.builder().build().readValue(JIRA_JSON, JiraWebhookPayload.class);

        assertThat(payload.webhookEvent()).isEqualTo("jira:issue_updated");
        assertThat(payload.issueKey()).isEqualTo("KAN-12");
        assertThat(payload.statusCategoryKey()).isEqualTo("indeterminate");
    }
}
