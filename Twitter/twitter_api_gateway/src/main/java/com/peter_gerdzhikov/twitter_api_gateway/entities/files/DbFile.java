package com.peter_gerdzhikov.twitter_api_gateway.entities.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.peter_gerdzhikov.twitter_api_gateway.entities.CommonEntity;

/**
 * The database record of one object in MinIO. {@code objectKey} is a random UUID string, never the client's
 * filename, and {@code contentType} is the type detected from the bytes, not the one the client claimed.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "db_files",
        uniqueConstraints = @UniqueConstraint(name = DbFile.OBJECT_KEY_CONSTRAINT, columnNames = "object_key")
)
public class DbFile extends CommonEntity {

    public static final String OBJECT_KEY_CONSTRAINT = "uk_db_files_object_key";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, length = 36)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
}
