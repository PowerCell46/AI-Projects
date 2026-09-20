package com.peter_gerdzhikov.url_shortener_backend.entities;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class CommonEntity {

    @Id
    private String id;

    /**
     * Ids are assigned by the application, so a null id can't mark an entity as new. The version
     * does: null means "never saved", which is what makes the first save an insert and lets
     * {@code @CreatedDate} fire.
     */
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
