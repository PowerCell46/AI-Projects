package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}
