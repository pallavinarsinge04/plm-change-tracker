package com.plmtracker.jira;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV ONLY. Creates one test issue so you can see the Jira connection work.
 * Only exists when the "dev" profile is active. Removed in story 4.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class JiraSmokeTestController {

    private final JiraClient jiraClient;

    public JiraSmokeTestController(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @PostMapping("/jira-test")
    public JiraIssue createTestIssue() {
        return jiraClient.createIssue(new CreateJiraIssueCommand(
                "P-0000",
                "Smoke test from PLM Change Tracker",
                "Created by the dev-only smoke test endpoint.",
                "LOW",
                "dev"));
    }
}
