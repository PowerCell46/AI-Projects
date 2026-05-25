package com.dewit.backend.mappers;

import com.dewit.backend.DTOs.category.CategoryResponse;
import com.dewit.backend.DTOs.task.TaskResponse;
import com.dewit.backend.entities.Category;

import java.util.List;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        List<TaskResponse> tasks = category.getTasks().stream()
                .map(TaskMapper::toResponse)
                .toList();

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCreatedAt(),
                category.getLastModifiedAt(),
                tasks
        );
    }
}
