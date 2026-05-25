package com.dewit.backend.DTOs.task;

import com.dewit.backend.entities.enumerations.TaskPriority;
import com.dewit.backend.entities.enumerations.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        LocalDateTime dueDate,
        TaskPriority priority,
        TaskStatus status,
        UUID categoryId,
        String categoryName,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt,
        LocalDateTime completedAt
) {
}
