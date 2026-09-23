package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.DuplicateInterestTopicNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.InterestTopicNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestTopicServiceImpl implements InterestTopicService {

    private final CategoryRepository categoryRepository;

    private final InterestTopicRepository interestTopicRepository;

    @Override
    @Transactional
    public InterestTopic create(String name, String description, String prompt, UUID categoryId) {
        Category category = findCategoryOrThrow(categoryId);

        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName(name);
        interestTopic.setDescription(description);
        interestTopic.setPrompt(prompt);
        interestTopic.setCategory(category);

        try {
            InterestTopic saved = interestTopicRepository.saveAndFlush(interestTopic);
            log.info("Created interest topic '{}'.", LogSanitizer.sanitize(saved.getName()));
            return saved;

        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInterestTopicNameException();
        }
    }

    @Override
    public Page<InterestTopic> findPage(UUID categoryId, Pageable pageable) {
        if (categoryId != null) {
            return interestTopicRepository.findByCategory_Id(categoryId, pageable);
        }
        return interestTopicRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public InterestTopic update(UUID topicId, String name, String description, String prompt, UUID categoryId) {
        InterestTopic interestTopic = interestTopicRepository
                .findById(topicId)
                .orElseThrow(InterestTopicNotFoundException::new);

        if (name != null) {
            interestTopic.setName(name);
        }
        if (description != null) {
            interestTopic.setDescription(description);
        }
        if (prompt != null) {
            interestTopic.setPrompt(prompt);
        }
        if (categoryId != null) {
            interestTopic.setCategory(findCategoryOrThrow(categoryId));
        }

        try {
            InterestTopic saved = interestTopicRepository.saveAndFlush(interestTopic);
            log.info("Updated interest topic '{}'.", topicId);
            return saved;

        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInterestTopicNameException();
        }
    }

    @Override
    @Transactional
    public void delete(UUID topicId) {
        if (!interestTopicRepository.existsById(topicId)) {
            throw new InterestTopicNotFoundException();
        }

        interestTopicRepository.deleteById(topicId);
        log.info("Deleted interest topic '{}'.", topicId);
    }

    @Override
    public List<UUID> findExistingIds(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return List.of();
        }

        return interestTopicRepository.findExistingIds(topicIds);
    }

    private Category findCategoryOrThrow(UUID categoryId) {
        return categoryRepository
                .findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
    }
}
