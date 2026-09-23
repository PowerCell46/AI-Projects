package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.CategoryRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.CategoryResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO request) {
        log.info("Received create category request.");
        Category category = categoryService.create(request.getName());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(category));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> listCategories() {
        // log.info("Received list categories request.");
        List<CategoryResponseDTO> categories = categoryService
                .findAllSortedByName()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(categories);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> renameCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequestDTO request
    ) {
        log.info("Received rename category request.");
        Category category = categoryService.rename(id, request.getName());

        return ResponseEntity.ok(toResponse(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        log.info("Received delete category request.");
        categoryService.delete(id);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    private CategoryResponseDTO toResponse(Category category) {
        return new CategoryResponseDTO(category.getId(), category.getName(), category.getCreatedAt());
    }
}
