package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryInUseException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.DuplicateCategoryNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.CategoryService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final InterestTopicRepository interestTopicRepository;

    @Override
    @Transactional
    public Category create(String name) {
        try {
            Category saved = categoryRepository.saveAndFlush(newCategory(name));
            log.info("Created category '{}'.", LogSanitizer.sanitize(saved.getName()));
            return saved;

        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryNameException();
        }
    }

    @Override
    public List<Category> findAllSortedByName() {
        return categoryRepository.findAll(Sort.by("name"));
    }

    @Override
    @Transactional
    public Category rename(UUID categoryId, String name) {
        Category category = categoryRepository
                .findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
        category.setName(name);

        try {
            Category saved = categoryRepository.saveAndFlush(category);
            log.info("Renamed category '{}' to '{}'.", categoryId, LogSanitizer.sanitize(saved.getName()));
            return saved;

        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryNameException();
        }
    }

    @Override
    @Transactional
    public void delete(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException();
        }

        if (interestTopicRepository.existsByCategory_Id(categoryId)) {
            throw new CategoryInUseException();
        }

        categoryRepository.deleteById(categoryId);
        log.info("Deleted category '{}'.", categoryId);
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }
}
