package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "interest_topics")
public class InterestTopic extends CommonEntity {

    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 1000)
    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @Size(max = 4000)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @JoinColumn(name = "category_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Category category;

    @PrePersist
    @PreUpdate
    private void lowercaseName() {
        if (name != null) {
            name = name.toLowerCase();
        }
    }
}
