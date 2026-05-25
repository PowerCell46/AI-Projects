package com.dewit.backend.services.implementations;

import com.dewit.backend.DTOs.category.CategoryCreateRequest;
import com.dewit.backend.DTOs.category.CategoryResponse;
import com.dewit.backend.DTOs.category.CategoryUpdateRequest;
import com.dewit.backend.entities.Category;
import com.dewit.backend.exceptions.CategoryHasTasksException;
import com.dewit.backend.exceptions.ResourceNotFoundException;
import com.dewit.backend.mappers.CategoryMapper;
import com.dewit.backend.repositories.CategoryRepository;
import com.dewit.backend.repositories.TaskRepository;
import com.dewit.backend.services.interfaces.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private static final String CATEGORY_NOT_FOUND_TEMPLATE = "Category %s not found";
    private static final String CATEGORY_HAS_TASKS_TEMPLATE =
            "Cannot delete category %s - it still has %d task(s)";

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    @Override
    public CategoryResponse create(CategoryCreateRequest request) {
        log.info("Creating category name='{}'", request.name());

        Category category = new Category();
        category.setName(request.name());
        Category saved = categoryRepository.save(category);

        log.info("Created category id={}", saved.getId());
        return CategoryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(Pageable pageable) {
        log.debug("Listing categories page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findAll(pageable).map(CategoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        log.debug("Fetching category id={}", id);
        return CategoryMapper.toResponse(loadCategoryOrThrow(id));
    }

    @Override
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        log.info("Updating category id={}", id);

        Category category = loadCategoryOrThrow(id);
        applyPartialUpdate(category, request);
        // JPA dirty checking inside the active transaction flushes changes on commit.

        log.info("Updated category id={}", id);
        return CategoryMapper.toResponse(category);
    }

    @Override
    public void delete(UUID id) {
        log.info("Deleting category id={}", id);

        Category category = loadCategoryOrThrow(id);
        long taskCount = taskRepository.countByCategory_Id(id);
        if (taskCount > 0) {
            log.warn("Refusing to delete category id={} - {} task(s) still reference it",
                    id, taskCount);
            throw new CategoryHasTasksException(
                    CATEGORY_HAS_TASKS_TEMPLATE.formatted(id, taskCount));
        }

        categoryRepository.delete(category);
        log.info("Deleted category id={}", id);
    }

    private void applyPartialUpdate(Category category, CategoryUpdateRequest request) {
        if (request.name() != null) {
            category.setName(request.name());
        }
    }

    private Category loadCategoryOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Category not found id={}", id);
                    return new ResourceNotFoundException(
                            CATEGORY_NOT_FOUND_TEMPLATE.formatted(id));
                });
    }
}
