package com.dewit.backend.DTOs.task;

import com.dewit.backend.entities.enumerations.TaskPriority;
import com.dewit.backend.entities.enumerations.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @FutureOrPresent LocalDateTime dueDate,
        @NotNull TaskPriority priority,
        @NotNull TaskStatus status,
        @NotNull UUID categoryId
) {
}
