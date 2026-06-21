package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.dashboard.AssetDistributionResponse;
import com.wealthbuilder.backend.entities.enumerations.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import com.wealthbuilder.backend.repositories.projections.AssetInvestment;
import lombok.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;


/**
 * Unit test for the dashboard service. The repository is mocked, so this verifies projection
 * mapping and user resolution rather than the grouped query itself.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final String USERNAME = "alice";

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Nested
    @DisplayName("Distribution")
    class Distribution {

        @Test
        void should_MapProjectionsToResponses_When_UserHasInvestments() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(holdingRepository.sumInvestedPerAssetByUser(any()))
                    .willReturn(List.of(
                            new StubInvestment(7L, "Stocks", new BigDecimal("900.00")),
                            new StubInvestment(3L, "Crypto", new BigDecimal("250.00"))));

            final List<AssetDistributionResponse> distribution = dashboardService.distribution(USERNAME);

            assertThat(distribution).hasSize(2);
            assertThat(distribution.getFirst().getAssetId()).isEqualTo(7L);
            assertThat(distribution.getFirst().getAssetName()).isEqualTo("Stocks");
            assertThat(distribution.getFirst().getAmountInvested()).isEqualByComparingTo("900.00");
            assertThat(distribution.get(1).getAssetId()).isEqualTo(3L);
        }

        @Test
        void should_ReturnEmptyList_When_UserHasNoInvestments() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user()));
            given(holdingRepository.sumInvestedPerAssetByUser(any())).willReturn(List.of());

            assertThat(dashboardService.distribution(USERNAME)).isEmpty();
        }

        @Test
        void should_ThrowUsernameNotFound_When_UserUnknown() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> dashboardService.distribution(USERNAME))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    private static User user() {
        final User user = new User(USERNAME, "hash", Role.USER);
        user.setId(1L);

        return user;
    }

    /**
     * Stand-in for the Spring Data alias projection so the mapping can be tested without a
     * database.
     */
    @Value
    static class StubInvestment implements AssetInvestment {

        Long assetId;

        String assetName;

        BigDecimal totalInvested;
    }
}
