package com.plmtracker.dto;

import com.plmtracker.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChangeRequestRequest(
        @NotBlank(message = "partNumber is required")
        @Size(max = 50, message = "partNumber must be at most 50 characters")
        String partNumber,

        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @NotBlank(message = "reason is required")
        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason,

        /** Optional. Defaults to MEDIUM. */
        Priority priority,

        /** Ignored if sent: the server sets it from the logged-in user. */
        String createdBy
) {}
