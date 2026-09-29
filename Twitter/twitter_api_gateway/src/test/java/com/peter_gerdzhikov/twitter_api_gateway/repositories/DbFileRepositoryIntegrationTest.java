package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DbFileRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Test
    void should_persist_and_read_back_every_column() {
        DbFile saved = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
        entityManager.clear();

        DbFile found = dbFileRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getObjectKey()).isEqualTo(saved.getObjectKey());
        assertThat(found.getContentType()).isEqualTo("image/png");
        assertThat(found.getSizeBytes()).isEqualTo(1024);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void should_reject_a_duplicate_object_key() {
        DbFile first = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
        DbFile second = TestEntities.newDbFile();
        second.setObjectKey(first.getObjectKey());

        assertThatThrownBy(() -> dbFileRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(DbFile.OBJECT_KEY_CONSTRAINT);
    }
}
