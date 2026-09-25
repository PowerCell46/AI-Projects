package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;

public interface CategoryService {

    Category create(String name);

    Page<Category> findPage(Pageable pageable);

    Category rename(UUID categoryId, String name);

    void delete(UUID categoryId);
}
