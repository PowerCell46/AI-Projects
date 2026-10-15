package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities;

import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.BatchSize;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interest_topics")
public class InterestTopic extends CommonEntity {

    public static final int MAX_CATEGORIES = 3;

    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Size(max = 1000)
    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @Size(max = 4000)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @BatchSize(size = 100)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "interest_topic_categories",
            joinColumns = @JoinColumn(name = "interest_topic_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NewsSchedule schedule = NewsSchedule.DAILY;

    @PreUpdate
    @PrePersist
    private void lowercaseName() {
        if (name != null) {
            name = name.toLowerCase(Locale.ROOT);
        }
    }
}
