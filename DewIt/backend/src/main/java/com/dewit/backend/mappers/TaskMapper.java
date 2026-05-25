package com.dewit.backend.mappers;

import com.dewit.backend.DTOs.task.TaskResponse;
import com.dewit.backend.entities.Category;
import com.dewit.backend.entities.Task;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        Category category = task.getCategory();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getPriority(),
                task.getStatus(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                task.getCreatedAt(),
                task.getLastModifiedAt(),
                task.getCompletedAt()
        );
    }
}
