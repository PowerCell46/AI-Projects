package com.dewit.backend.services.interfaces;

import com.dewit.backend.DTOs.category.CategoryCreateRequest;
import com.dewit.backend.DTOs.category.CategoryResponse;
import com.dewit.backend.DTOs.category.CategoryUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Application service contract for {@link com.dewit.backend.entities.Category} resources.
 * Encapsulates persistence operations behind a stable DTO surface and centralises
 * domain-level guards (e.g., refusing to delete a category that still owns tasks).
 */
public interface CategoryService {

    CategoryResponse create(CategoryCreateRequest request);

    Page<CategoryResponse> findAll(Pageable pageable);

    CategoryResponse findById(UUID id);

    CategoryResponse update(UUID id, CategoryUpdateRequest request);

    void delete(UUID id);
}
