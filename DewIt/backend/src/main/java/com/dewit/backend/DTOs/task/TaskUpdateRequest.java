package com.dewit.backend.DTOs.task;

import com.dewit.backend.entities.enumerations.TaskPriority;
import com.dewit.backend.entities.enumerations.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 2000) String description,
        LocalDateTime dueDate,
        TaskPriority priority,
        TaskStatus status,
        UUID categoryId
) {
}
