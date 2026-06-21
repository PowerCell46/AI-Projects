package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.PageResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingFilter;
import com.wealthbuilder.backend.DTOs.holding.HoldingRequest;
import com.wealthbuilder.backend.DTOs.holding.HoldingResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingSummaryResponse;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.entities.enumerations.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.exceptions.holding.HoldingNotFoundException;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * Unit test for the holding service. All collaborators are mocked, so this verifies the
 * aggregation math, the forced newest-first ordering, ownership enforcement and not-found
 * branching rather than persistence behaviour.
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceImplTest {

    private static final String OWNER = "alice";

    private static final String INTRUDER = "mallory";

    private static final Long ASSET_ID = 7L;

    private static final Long HOLDING_ID = 42L;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HoldingServiceImpl holdingService;

    @Nested
    @DisplayName("List holdings")
    class ListHoldings {

        @Test
        void should_ReturnPageResponse_When_AssetExists() {
            givenUserAndAssetExist();
            final AssetHolding holding = holding(HOLDING_ID, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 1, 1));
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(holding), PageRequest.of(0, 20), 1));

            final PageResponse<HoldingResponse> response =
                    holdingService.listHoldings(OWNER, ASSET_ID, noFilter(), PageRequest.of(0, 20));

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent().getFirst().getPrice()).isEqualByComparingTo("50");
        }

        // The service lowercases the fragment and wraps it as a %contains% LIKE pattern; the date
        // bounds pass through unchanged.
        @Test
        void should_PassLoweredLikePatternAndDateRangeToRepository_When_Listing() {
            givenUserAndAssetExist();
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));
            final HoldingFilter filter = HoldingFilter.of("App", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));

            holdingService.listHoldings(OWNER, ASSET_ID, filter, PageRequest.of(0, 20));

            verify(holdingRepository).search(
                    any(User.class),
                    any(Asset.class),
                    eq("%app%"),
                    eq(LocalDate.of(2026, 1, 1)),
                    eq(LocalDate.of(2026, 6, 1)),
                    any(Pageable.class));
        }

        @Test
        void should_PassNullPattern_When_NameFilterBlank() {
            givenUserAndAssetExist();
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            holdingService.listHoldings(OWNER, ASSET_ID, HoldingFilter.of("  ", null, null), PageRequest.of(0, 20));

            verify(holdingRepository).search(
                    any(User.class), any(Asset.class), isNull(), isNull(), isNull(), any(Pageable.class));
        }

        // The client's incoming sort must be discarded in favour of date DESC, id DESC so paging
        // stays deterministic regardless of what the SPA requests.
        @Test
        void should_OverrideIncomingSortToDateDescIdDesc_When_Listing() {
            givenUserAndAssetExist();
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));
            final Pageable incoming = PageRequest.of(2, 15, Sort.by(Sort.Direction.ASC, "name"));

            holdingService.listHoldings(OWNER, ASSET_ID, noFilter(), incoming);

            final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(holdingRepository).search(
                    any(User.class), any(Asset.class), any(), any(), any(), captor.capture());
            final Pageable used = captor.getValue();
            assertThat(used.getPageNumber()).isEqualTo(2);
            assertThat(used.getPageSize()).isEqualTo(15);
            assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "date", "id"));
        }

        @Test
        void should_ThrowNotFound_When_AssetMissing() {
            given(userRepository.findByUsername(OWNER)).willReturn(Optional.of(user(OWNER)));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.listHoldings(OWNER, ASSET_ID, noFilter(), PageRequest.of(0, 20)))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Summarize")
    class Summarize {

        @Test
        void should_ReturnEmptySummary_When_NoHoldings() {
            givenUserAndAssetExist();
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            final HoldingSummaryResponse summary = holdingService.summarize(OWNER, ASSET_ID, noFilter());

            assertThat(summary.getHoldingCount()).isZero();
            assertThat(summary.getAveragePrice()).isNull();
            assertThat(summary.getQuantitySum()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getAmountSum()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getPeriodStart()).isNull();
            assertThat(summary.getPeriodEnd()).isNull();
        }

        // Two holdings: 100 for 2 units and 300 for 4 units. Weighted average price (cost basis)
        // = amount sum / quantity sum = 400 / 6 = 66.66666667. Quantity sum = 6, amount sum = 400,
        // period spans the earliest and latest dates.
        @Test
        void should_ComputeWeightedAveragePriceAndSumsAndSpan_When_HoldingsExist() {
            givenUserAndAssetExist();
            final AssetHolding first = holding(1L, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 3, 10));
            final AssetHolding second = holding(2L, OWNER, new BigDecimal("300.0000"),
                    new BigDecimal("4.00000000"), LocalDate.of(2026, 1, 5));
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(first, second)));

            final HoldingSummaryResponse summary = holdingService.summarize(OWNER, ASSET_ID, noFilter());

            assertThat(summary.getHoldingCount()).isEqualTo(2);
            assertThat(summary.getAveragePrice()).isEqualByComparingTo("66.66666667");
            assertThat(summary.getQuantitySum()).isEqualByComparingTo("6");
            assertThat(summary.getAmountSum()).isEqualByComparingTo("400");
            assertThat(summary.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 5));
            assertThat(summary.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        // The summary must aggregate over the same filtered set as the listing, so the filter is
        // lowercased/wrapped and forwarded to the repository search exactly as it is for the list.
        @Test
        void should_ForwardFilterToRepositorySearch_When_Summarizing() {
            givenUserAndAssetExist();
            given(holdingRepository.search(any(User.class), any(Asset.class), any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));
            final HoldingFilter filter = HoldingFilter.of("Net", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));

            holdingService.summarize(OWNER, ASSET_ID, filter);

            verify(holdingRepository).search(
                    any(User.class),
                    any(Asset.class),
                    eq("%net%"),
                    eq(LocalDate.of(2026, 1, 1)),
                    eq(LocalDate.of(2026, 6, 1)),
                    any(Pageable.class));
        }

        @Test
        void should_ThrowNotFound_When_AssetMissing() {
            given(userRepository.findByUsername(OWNER)).willReturn(Optional.of(user(OWNER)));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.summarize(OWNER, ASSET_ID, noFilter()))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        void should_SaveHoldingOwnedByCallerWithRequestFields_When_AssetExists() {
            final User owner = user(OWNER);
            final Asset asset = asset();
            given(userRepository.findByUsername(OWNER)).willReturn(Optional.of(owner));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));

            holdingService.create(OWNER, ASSET_ID, request());

            final ArgumentCaptor<AssetHolding> saved = ArgumentCaptor.forClass(AssetHolding.class);
            verify(holdingRepository).save(saved.capture());
            assertThat(saved.getValue().getAsset()).isSameAs(asset);
            assertThat(saved.getValue().getUser()).isSameAs(owner);
            assertThat(saved.getValue().getName()).isEqualTo("Apple shares");
            assertThat(saved.getValue().getBoughtForAmount()).isEqualByComparingTo("1500.0000");
            assertThat(saved.getValue().getUnit()).isEqualTo("shares");
            assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("10.00000000");
            assertThat(saved.getValue().getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(saved.getValue().getNote()).isEqualTo("Bought on the dip.");
        }

        @Test
        void should_ThrowNotFoundAndNotSave_When_AssetMissing() {
            given(userRepository.findByUsername(OWNER)).willReturn(Optional.of(user(OWNER)));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.create(OWNER, ASSET_ID, request()))
                    .isInstanceOf(AssetNotFoundException.class);

            verify(holdingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        void should_MutateHoldingFields_When_CallerOwnsHolding() {
            final AssetHolding holding = holding(HOLDING_ID, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 1, 1));
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.of(holding));

            holdingService.update(OWNER, HOLDING_ID, request());

            assertThat(holding.getName()).isEqualTo("Apple shares");
            assertThat(holding.getBoughtForAmount()).isEqualByComparingTo("1500.0000");
            assertThat(holding.getUnit()).isEqualTo("shares");
            assertThat(holding.getQuantity()).isEqualByComparingTo("10.00000000");
            assertThat(holding.getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(holding.getNote()).isEqualTo("Bought on the dip.");
        }

        @Test
        void should_ThrowNotFound_When_HoldingMissing() {
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.update(OWNER, HOLDING_ID, request()))
                    .isInstanceOf(HoldingNotFoundException.class);
        }

        @Test
        void should_ThrowAccessDenied_When_CallerDoesNotOwnHolding() {
            final AssetHolding holding = holding(HOLDING_ID, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 1, 1));
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.of(holding));

            assertThatThrownBy(() -> holdingService.update(INTRUDER, HOLDING_ID, request()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        void should_DeleteHolding_When_CallerOwnsHolding() {
            final AssetHolding holding = holding(HOLDING_ID, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 1, 1));
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.of(holding));

            holdingService.delete(OWNER, HOLDING_ID);

            verify(holdingRepository).delete(holding);
        }

        @Test
        void should_ThrowNotFoundAndNotDelete_When_HoldingMissing() {
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.delete(OWNER, HOLDING_ID))
                    .isInstanceOf(HoldingNotFoundException.class);

            verify(holdingRepository, never()).delete(any());
        }

        @Test
        void should_ThrowAccessDeniedAndNotDelete_When_CallerDoesNotOwnHolding() {
            final AssetHolding holding = holding(HOLDING_ID, OWNER, new BigDecimal("100.0000"),
                    new BigDecimal("2.00000000"), LocalDate.of(2026, 1, 1));
            given(holdingRepository.findById(HOLDING_ID)).willReturn(Optional.of(holding));

            assertThatThrownBy(() -> holdingService.delete(INTRUDER, HOLDING_ID))
                    .isInstanceOf(AccessDeniedException.class);

            verify(holdingRepository, never()).delete(any());
        }
    }

    private void givenUserAndAssetExist() {
        given(userRepository.findByUsername(OWNER)).willReturn(Optional.of(user(OWNER)));
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset()));
    }

    private static HoldingFilter noFilter() {
        return HoldingFilter.of(null, null, null);
    }

    private static Asset asset() {
        final Asset asset = new Asset("Stocks", "Equities.", "data:image/png;base64,aGVsbG8=", "stocks.png");
        asset.setId(ASSET_ID);

        return asset;
    }

    private static User user(String username) {
        final User user = new User(username, "hash", Role.USER);
        user.setId(username.equals(OWNER) ? 1L : 2L);

        return user;
    }

    private static AssetHolding holding(
            Long id, String ownerUsername, BigDecimal amount, BigDecimal quantity, LocalDate date) {
        final AssetHolding holding = new AssetHolding(
                asset(), user(ownerUsername), "Old name", amount, "shares", quantity, date, "Old note");
        holding.setId(id);

        return holding;
    }

    private static HoldingRequest request() {
        final HoldingRequest request = new HoldingRequest();
        request.setName("Apple shares");
        request.setBoughtForAmount(new BigDecimal("1500.0000"));
        request.setUnit("shares");
        request.setQuantity(new BigDecimal("10.00000000"));
        request.setDate(LocalDate.of(2026, 2, 1));
        request.setNote("Bought on the dip.");

        return request;
    }
}
