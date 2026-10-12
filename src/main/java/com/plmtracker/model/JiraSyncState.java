package com.plmtracker.model;

/** Whether the change request has a matching Jira issue yet. */
public enum JiraSyncState {
    PENDING,   // saved locally, Jira issue not created yet
    SYNCED,    // Jira issue exists and jiraKey is filled
    FAILED     // Jira call failed, will be retried
}
