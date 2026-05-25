package com.dewit.backend.controllers;

import com.dewit.backend.DTOs.task.TaskCountFilter;
import com.dewit.backend.DTOs.task.TaskCountResponse;
import com.dewit.backend.DTOs.task.TaskCreateRequest;
import com.dewit.backend.DTOs.task.TaskResponse;
import com.dewit.backend.DTOs.task.TaskUpdateRequest;
import com.dewit.backend.services.interfaces.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        TaskResponse created = taskService.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @GetMapping
    public Page<TaskResponse> findAll(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return taskService.findAll(pageable);
    }

    /**
     * Returns the count of tasks matching the given date-bucket filter.
     * NOTE: this endpoint is not consumed by the current frontend, which computes
     * bucket counts client-side from the paginated task list. If server-side counts
     * are ever needed (e.g. to work around the pagination cap), wire this from a
     * dedicated dashboard stats hook instead of re-deriving counts from the task list.
     */
    @GetMapping("/count")
    public TaskCountResponse count(@RequestParam TaskCountFilter filter) {
        return new TaskCountResponse(taskService.count(filter));
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable UUID id) {
        return taskService.findById(id);
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        taskService.delete(id);
    }
}
