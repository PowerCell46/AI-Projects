package com.wealthbuilder.backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit test for the shared money arithmetic. Pins the unit-price scale and the HALF_UP
 * rounding so every place that derives a price stays in agreement.
 */
class MoneyTest {

    @Nested
    @DisplayName("unitPrice")
    class UnitPrice {

        @Test
        void should_DivideExactly_When_DivisionTerminates() {
            final BigDecimal price = Money.unitPrice(new BigDecimal("100"), new BigDecimal("4"));

            assertThat(price).isEqualByComparingTo("25");
        }

        @Test
        void should_ProduceScaleOfEight_When_Computed() {
            final BigDecimal price = Money.unitPrice(new BigDecimal("100"), new BigDecimal("4"));

            assertThat(price.scale()).isEqualTo(Money.PRICE_SCALE);
        }

        // 1 / 3 = 0.33333333|3..., the 9th digit is 3 so HALF_UP leaves the 8th unchanged.
        @Test
        void should_RoundDown_When_NinthDigitIsBelowHalf() {
            final BigDecimal price = Money.unitPrice(BigDecimal.ONE, new BigDecimal("3"));

            assertThat(price).isEqualByComparingTo("0.33333333");
        }

        // 2 / 3 = 0.66666666|6..., the 9th digit is 6 so HALF_UP rounds the 8th up.
        @Test
        void should_RoundUp_When_NinthDigitIsAtOrAboveHalf() {
            final BigDecimal price = Money.unitPrice(new BigDecimal("2"), new BigDecimal("3"));

            assertThat(price).isEqualByComparingTo("0.66666667");
        }

        @Test
        void should_HandleHighPrecisionQuantity_When_DividingFractionalUnits() {
            final BigDecimal price = Money.unitPrice(new BigDecimal("100.00"), new BigDecimal("0.50000000"));

            assertThat(price).isEqualByComparingTo("200");
        }

        // A zero quantity must not blow up with ArithmeticException; it has no unit price, so zero.
        @Test
        void should_ReturnZeroAtPriceScale_When_QuantityIsZero() {
            final BigDecimal price = Money.unitPrice(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThat(price).isEqualByComparingTo("0");
            assertThat(price.scale()).isEqualTo(Money.PRICE_SCALE);
        }
    }
}
