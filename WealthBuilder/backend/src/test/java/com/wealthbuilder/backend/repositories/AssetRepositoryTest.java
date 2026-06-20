package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Asset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;


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
}
