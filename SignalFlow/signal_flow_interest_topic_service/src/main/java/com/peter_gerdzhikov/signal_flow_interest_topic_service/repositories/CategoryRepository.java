package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
