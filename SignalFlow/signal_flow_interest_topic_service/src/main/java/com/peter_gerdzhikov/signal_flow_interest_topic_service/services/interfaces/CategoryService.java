package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;

public interface CategoryService {

    Category create(String name);

    List<Category> findAllSortedByName();

    Category rename(UUID categoryId, String name);

    void delete(UUID categoryId);
}
