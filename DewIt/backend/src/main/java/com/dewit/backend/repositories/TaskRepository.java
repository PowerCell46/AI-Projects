package com.dewit.backend.repositories;

import com.dewit.backend.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    long countByCategory_Id(UUID categoryId);

    long countByDueDateGreaterThanEqualAndDueDateLessThan(LocalDateTime start, LocalDateTime endExclusive);

    long countByDueDateBefore(LocalDateTime cutoff);

    long countByDueDateGreaterThanEqual(LocalDateTime cutoff);
}
