package com.dewit.backend.services.implementations;

import com.dewit.backend.DTOs.task.TaskCountFilter;
import com.dewit.backend.DTOs.task.TaskCreateRequest;
import com.dewit.backend.DTOs.task.TaskResponse;
import com.dewit.backend.DTOs.task.TaskUpdateRequest;
import com.dewit.backend.entities.Category;
import com.dewit.backend.entities.Task;
import com.dewit.backend.entities.enumerations.TaskStatus;
import com.dewit.backend.exceptions.ResourceNotFoundException;
import com.dewit.backend.mappers.TaskMapper;
import com.dewit.backend.repositories.CategoryRepository;
import com.dewit.backend.repositories.TaskRepository;
import com.dewit.backend.services.interfaces.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    private static final String TASK_NOT_FOUND_TEMPLATE = "Task %s not found";
    private static final String CATEGORY_NOT_FOUND_TEMPLATE = "Category %s not found";

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public TaskResponse create(TaskCreateRequest request) {
        log.info("Creating task title='{}' categoryId={}", request.title(), request.categoryId());

        Category category = loadCategoryOrThrow(request.categoryId());
        Task task = new Task();

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());

        task.setPriority(request.priority());
        task.setStatus(request.status());

        if (request.status() == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
        }

        task.setCategory(category);

        Task saved = taskRepository.save(task);
        log.info("Created task id={}", saved.getId());
        return TaskMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> findAll(Pageable pageable) {
        log.debug("Listing tasks page={} size={}", pageable.getPageNumber(), pageable.getPageSize());

        return taskRepository
                .findAll(pageable)
                .map(TaskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(UUID id) {
        log.debug("Fetching task id={}", id);
        return TaskMapper.toResponse(loadTaskOrThrow(id));
    }

    @Override
    public TaskResponse update(UUID id, TaskUpdateRequest request) {
        log.info("Updating task id={}", id);

        Task task = loadTaskOrThrow(id);
        applyPartialUpdate(task, request);

        // JPA dirty checking inside the active transaction flushes changes on commit.

        log.info("Updated task id={}", id);
        return TaskMapper.toResponse(task);
    }

    @Override
    public void delete(UUID id) {
        log.info("Deleting task id={}", id);

        Task task = loadTaskOrThrow(id);
        taskRepository.delete(task);

        log.info("Deleted task id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(TaskCountFilter filter) {
        log.debug("Counting tasks with filter={}", filter);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        long count = switch (filter) {
            case TODAY -> taskRepository.countByDueDateGreaterThanEqualAndDueDateLessThan(startOfToday, startOfTomorrow);
            case OVERDUE -> taskRepository.countByDueDateBefore(startOfToday);
            case UPCOMING -> taskRepository.countByDueDateGreaterThanEqual(startOfTomorrow);
        };

        log.debug("Counted {} task(s) for filter={}", count, filter);
        return count;
    }

    private void applyPartialUpdate(Task task, TaskUpdateRequest request) {
        if (request.title() != null) {
            task.setTitle(request.title());
        }

        if (request.description() != null) {
            task.setDescription(request.description());
        }

        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        if (request.priority() != null) {
            task.setPriority(request.priority());
        }

        if (request.status() != null) {
            TaskStatus newStatus = request.status();

            if (newStatus == TaskStatus.COMPLETED && task.getStatus() != TaskStatus.COMPLETED) {
                task.setCompletedAt(LocalDateTime.now());

            } else if (newStatus != TaskStatus.COMPLETED && task.getStatus() == TaskStatus.COMPLETED) {
                task.setCompletedAt(null);
            }

            task.setStatus(newStatus);
        }

        if (request.categoryId() != null) {
            task.setCategory(loadCategoryOrThrow(request.categoryId()));
        }
    }

    private Task loadTaskOrThrow(UUID id) {
        return taskRepository
                .findById(id)
                .orElseThrow(() -> {
                    log.warn("Task not found id={}", id);
                    return new ResourceNotFoundException(TASK_NOT_FOUND_TEMPLATE.formatted(id));
                });
    }

    private Category loadCategoryOrThrow(UUID id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> {
                    log.warn("Category not found id={}", id);

                    return new ResourceNotFoundException(
                            CATEGORY_NOT_FOUND_TEMPLATE.formatted(id));
                });
    }
}
