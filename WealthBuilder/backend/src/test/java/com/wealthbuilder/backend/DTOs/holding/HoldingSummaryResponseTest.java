package com.wealthbuilder.backend.DTOs.holding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit test for the summary DTO factories. Pins the scales of the empty response so it
 * serializes with the same shape as a populated one rather than bare scale-zero zeros.
 */
class HoldingSummaryResponseTest {

    @Test
    @DisplayName("empty() zeros match the entity column scales")
    void should_UseColumnScalesForZeros_When_Empty() {
        final HoldingSummaryResponse summary = HoldingSummaryResponse.empty();

        assertThat(summary.getHoldingCount()).isZero();
        assertThat(summary.getAveragePrice()).isNull();
        assertThat(summary.getAmountSum().scale()).isEqualTo(4);
        assertThat(summary.getQuantitySum().scale()).isEqualTo(8);
        assertThat(summary.getAmountSum()).isEqualByComparingTo("0");
        assertThat(summary.getQuantitySum()).isEqualByComparingTo("0");
    }
}
