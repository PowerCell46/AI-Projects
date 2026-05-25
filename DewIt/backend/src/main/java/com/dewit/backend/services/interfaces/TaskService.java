package com.dewit.backend.services.interfaces;

import com.dewit.backend.DTOs.task.TaskCountFilter;
import com.dewit.backend.DTOs.task.TaskCreateRequest;
import com.dewit.backend.DTOs.task.TaskResponse;
import com.dewit.backend.DTOs.task.TaskUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Application service contract for {@link com.dewit.backend.entities.Task} resources.
 * Handles CRUD, partial updates with null-as-unchanged semantics, and the
 * date-bucket count queries that power the dashboard summary widgets.
 */
public interface TaskService {

    TaskResponse create(TaskCreateRequest request);

    Page<TaskResponse> findAll(Pageable pageable);

    TaskResponse findById(UUID id);

    TaskResponse update(UUID id, TaskUpdateRequest request);

    void delete(UUID id);

    long count(TaskCountFilter filter);
}
