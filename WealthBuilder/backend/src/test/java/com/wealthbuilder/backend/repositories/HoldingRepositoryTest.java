package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.entities.enumerations.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.projections.AssetInvestment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * JPA slice test for the custom holding queries. Runs against an in-memory H2 database in
 * PostgreSQL compatibility mode so the aggregations, grouping and ownership scoping are
 * exercised against real SQL rather than a mock.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:holdingdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class HoldingRepositoryTest {

    private static final String IMAGE = "data:image/png;base64,aGVsbG8=";

    private static final String IMAGE_NAME = "image.png";

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    private User owner;

    private User other;

    private Asset stocks;

    private Asset crypto;

    @BeforeEach
    void seedFixtures() {
        owner = userRepository.save(new User("alice", "hash", Role.USER));
        other = userRepository.save(new User("bob", "hash", Role.USER));
        stocks = assetRepository.save(new Asset("Stocks", "Equities.", IMAGE, IMAGE_NAME));
        crypto = assetRepository.save(new Asset("Crypto", "Digital assets.", IMAGE, IMAGE_NAME));
    }

    @Nested
    @DisplayName("sumInvestedByUser")
    class SumInvestedByUser {

        @Test
        void should_ReturnZero_When_UserHasNoHoldings() {
            assertThat(holdingRepository.sumInvestedByUser(owner)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void should_ReturnSumAcrossAllAssets_When_UserHasHoldings() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(owner, crypto, "250.0000", "2.00000000", LocalDate.of(2026, 2, 1));

            assertThat(holdingRepository.sumInvestedByUser(owner)).isEqualByComparingTo("350.0000");
        }

        @Test
        void should_ExcludeOtherUsersHoldings_When_Summing() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(other, stocks, "999.0000", "1.00000000", LocalDate.of(2026, 1, 1));

            assertThat(holdingRepository.sumInvestedByUser(owner)).isEqualByComparingTo("100.0000");
        }
    }

    @Nested
    @DisplayName("sumInvestedPerAssetByUser")
    class SumInvestedPerAssetByUser {

        @Test
        void should_GroupSumAndOrderByTotalDescending_When_UserHasHoldings() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(owner, stocks, "50.0000", "1.00000000", LocalDate.of(2026, 1, 2));
            persistHolding(owner, crypto, "400.0000", "2.00000000", LocalDate.of(2026, 1, 3));

            final List<AssetInvestment> distribution = holdingRepository.sumInvestedPerAssetByUser(owner);

            assertThat(distribution).hasSize(2);
            assertThat(distribution.getFirst().getAssetId()).isEqualTo(crypto.getId());
            assertThat(distribution.getFirst().getAssetName()).isEqualTo("Crypto");
            assertThat(distribution.getFirst().getTotalInvested()).isEqualByComparingTo("400.0000");
            assertThat(distribution.get(1).getAssetId()).isEqualTo(stocks.getId());
            assertThat(distribution.get(1).getTotalInvested()).isEqualByComparingTo("150.0000");
        }

        @Test
        void should_ReturnEmpty_When_UserHasNoHoldings() {
            assertThat(holdingRepository.sumInvestedPerAssetByUser(owner)).isEmpty();
        }

        @Test
        void should_ScopeToTheGivenUser_When_AnotherUserAlsoInvested() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(other, crypto, "999.0000", "1.00000000", LocalDate.of(2026, 1, 1));

            final List<AssetInvestment> distribution = holdingRepository.sumInvestedPerAssetByUser(owner);

            assertThat(distribution).hasSize(1);
            assertThat(distribution.getFirst().getAssetId()).isEqualTo(stocks.getId());
        }
    }

    @Nested
    @DisplayName("Paginated findByUserAndAsset")
    class PaginatedFindByUserAndAsset {

        @Test
        void should_RespectTheRequestedSort_When_Paging() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 10));
            persistHolding(owner, stocks, "200.0000", "1.00000000", LocalDate.of(2026, 3, 20));
            persistHolding(owner, stocks, "300.0000", "1.00000000", LocalDate.of(2026, 2, 15));

            final Page<AssetHolding> page = holdingRepository.findByUserAndAsset(
                    owner, stocks, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "date")));

            assertThat(page.getContent())
                    .extracting(AssetHolding::getDate)
                    .containsExactly(
                            LocalDate.of(2026, 3, 20),
                            LocalDate.of(2026, 2, 15),
                            LocalDate.of(2026, 1, 10));
        }

        @Test
        void should_ScopeToUserAndAsset_When_Paging() {
            persistHolding(owner, stocks, "100.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(owner, crypto, "200.0000", "1.00000000", LocalDate.of(2026, 1, 1));
            persistHolding(other, stocks, "300.0000", "1.00000000", LocalDate.of(2026, 1, 1));

            final Page<AssetHolding> page = holdingRepository.findByUserAndAsset(
                    owner, stocks, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "date")));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().getFirst().getBoughtForAmount()).isEqualByComparingTo("100.0000");
        }
    }

    @Nested
    @DisplayName("Filtered search")
    class FilteredSearch {

        // The pattern is pre-lowercased by the service; the column is lowered in SQL, so an
        // uppercase holding name still matches a lowercase pattern.
        @Test
        void should_MatchLoweredNameAgainstPattern_When_PatternGiven() {
            persistNamedHolding(owner, stocks, "APPLE", LocalDate.of(2026, 1, 1));
            persistNamedHolding(owner, stocks, "Microsoft", LocalDate.of(2026, 1, 2));

            final Page<AssetHolding> page = holdingRepository.search(
                    owner, stocks, "%app%", null, null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(AssetHolding::getName)
                    .containsExactly("APPLE");
        }

        @Test
        void should_RestrictToInclusiveDateRange_When_FromAndToGiven() {
            persistNamedHolding(owner, stocks, "Early", LocalDate.of(2026, 1, 1));
            persistNamedHolding(owner, stocks, "InRange", LocalDate.of(2026, 3, 15));
            persistNamedHolding(owner, stocks, "Late", LocalDate.of(2026, 6, 1));

            final Page<AssetHolding> page = holdingRepository.search(
                    owner, stocks, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 1), PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(AssetHolding::getName)
                    .containsExactly("InRange");
        }

        @Test
        void should_ReturnAllForUserAndAsset_When_NoCriteriaGiven() {
            persistNamedHolding(owner, stocks, "One", LocalDate.of(2026, 1, 1));
            persistNamedHolding(owner, crypto, "Other", LocalDate.of(2026, 1, 1));
            persistNamedHolding(other, stocks, "NotMine", LocalDate.of(2026, 1, 1));

            final Page<AssetHolding> page = holdingRepository.search(
                    owner, stocks, null, null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().getFirst().getName()).isEqualTo("One");
        }
    }

    private void persistHolding(User user, Asset asset, String amount, String quantity, LocalDate date) {
        holdingRepository.save(new AssetHolding(
                asset,
                user,
                "Holding",
                new BigDecimal(amount),
                new BigDecimal(quantity),
                date,
                null));
    }

    private void persistNamedHolding(User user, Asset asset, String name, LocalDate date) {
        holdingRepository.save(new AssetHolding(
                asset,
                user,
                name,
                new BigDecimal("100.0000"),
                new BigDecimal("1.00000000"),
                date,
                null));
    }
}
