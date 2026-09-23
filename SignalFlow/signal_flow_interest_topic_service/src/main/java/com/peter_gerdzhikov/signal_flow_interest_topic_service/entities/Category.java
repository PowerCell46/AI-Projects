package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "categories")
public class Category extends CommonEntity {

    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @PreUpdate
    @PrePersist
    private void lowercaseName() {
        if (name != null) {
            name = name.toLowerCase();
        }
    }
}
