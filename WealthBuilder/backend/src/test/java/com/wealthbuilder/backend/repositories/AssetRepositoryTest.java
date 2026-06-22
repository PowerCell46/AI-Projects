package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Asset;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * JPA slice test for the custom asset queries. Runs against an in-memory H2 database in
 * PostgreSQL compatibility mode, so the case-insensitive uniqueness checks are exercised
 * against a real query rather than a mock.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:assetdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AssetRepositoryTest {

    private static final String IMAGE = "data:image/png;base64,aGVsbG8=";

    private static final String IMAGE_NAME = "image.png";

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedAsset() {
        assetRepository.save(new Asset("Stocks", "Equity instruments.", IMAGE, IMAGE_NAME));
    }

    @Nested
    @DisplayName("existsByNameIgnoreCase")
    class ExistsByNameIgnoreCase {

        @Test
        void should_ReturnTrue_When_NameMatchesExactly() {
            assertThat(assetRepository.existsByNameIgnoreCase("Stocks")).isTrue();
        }

        @Test
        void should_ReturnTrue_When_NameMatchesDifferentCase() {
            assertThat(assetRepository.existsByNameIgnoreCase("STOCKS")).isTrue();
            assertThat(assetRepository.existsByNameIgnoreCase("stocks")).isTrue();
        }

        @Test
        void should_ReturnFalse_When_NoSuchName() {
            assertThat(assetRepository.existsByNameIgnoreCase("Crypto")).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByNameIgnoreCaseAndIdNot")
    class ExistsByNameIgnoreCaseAndIdNot {

        @Test
        void should_ReturnFalse_When_OnlyMatchIsTheExcludedAsset() {
            final Asset stocks = assetRepository.findAll().getFirst();

            assertThat(assetRepository.existsByNameIgnoreCaseAndIdNot("stocks", stocks.getId()))
                    .isFalse();
        }

        @Test
        void should_ReturnTrue_When_AnotherAssetHasTheNameCaseInsensitively() {
            final Asset stocks = assetRepository.findAll().getFirst();
            final Asset crypto = assetRepository.save(new Asset("Crypto", "Digital assets.", IMAGE, IMAGE_NAME));

            assertThat(assetRepository.existsByNameIgnoreCaseAndIdNot("CRYPTO", stocks.getId()))
                    .isTrue();
            assertThat(crypto.getId()).isNotNull();
        }

        @Test
        void should_ReturnFalse_When_NoOtherAssetHasTheName() {
            final Asset stocks = assetRepository.findAll().getFirst();

            assertThat(assetRepository.existsByNameIgnoreCaseAndIdNot("Bonds", stocks.getId()))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Optimistic locking")
    class OptimisticLocking {

        // Two writers load the same asset; the one that commits second holds a stale @Version and
        // must be rejected rather than silently overwriting the first — the lost-update guarantee.
        @Test
        void should_RejectStaleUpdate_When_VersionChangedConcurrently() {
            final Long id = assetRepository.findAll().getFirst().getId();
            entityManager.clear();

            final Asset staleWriter = assetRepository.findById(id).orElseThrow();
            entityManager.detach(staleWriter);

            final Asset winningWriter = assetRepository.findById(id).orElseThrow();
            winningWriter.setDescription("Committed first.");
            assetRepository.saveAndFlush(winningWriter);
            entityManager.clear();

            staleWriter.setDescription("Committed second on a stale version.");

            assertThatThrownBy(() -> assetRepository.saveAndFlush(staleWriter))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }
}
