package com.plmtracker.model;

/** Business status of a change request. Mirrors the Jira workflow columns. */
public enum ChangeRequestStatus {
    OPEN,        // Jira: To Do
    IN_REVIEW,   // Jira: In Progress
    CLOSED       // Jira: Done
}
