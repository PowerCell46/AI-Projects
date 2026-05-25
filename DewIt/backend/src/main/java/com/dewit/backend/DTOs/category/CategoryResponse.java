package com.dewit.backend.DTOs.category;

import com.dewit.backend.DTOs.task.TaskResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt,
        List<TaskResponse> tasks
) {
}
