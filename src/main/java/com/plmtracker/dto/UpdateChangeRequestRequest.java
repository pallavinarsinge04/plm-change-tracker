package com.plmtracker.dto;

import com.plmtracker.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateChangeRequestRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @NotBlank(message = "reason is required")
        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason,

        @NotNull(message = "priority is required")
        Priority priority
) {}
