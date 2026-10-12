package com.plmtracker.dto;

import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.model.JiraSyncState;
import com.plmtracker.model.Priority;

import java.time.Instant;

public record ChangeRequestResponse(
        Long id,
        String partNumber,
        String title,
        String reason,
        Priority priority,
        ChangeRequestStatus status,
        JiraSyncState jiraSyncState,
        String jiraKey,
        String jiraUrl,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChangeRequestResponse from(ChangeRequest c) {
        return new ChangeRequestResponse(
                c.getId(),
                c.getPartNumber(),
                c.getTitle(),
                c.getReason(),
                c.getPriority(),
                c.getStatus(),
                c.getJiraSyncState(),
                c.getJiraKey(),
                c.getJiraUrl(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
