package com.wealthbuilder.backend.DTOs.holding;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * Aggregation over a filtered set of a user's holdings for one asset. {@code averagePrice} is
 * the weighted average price ({@code amountSum / quantitySum}, i.e. the real cost basis). When
 * no holdings match the monetary figures are zero and the price/period fields are null.
 */
@Value
public class HoldingSummaryResponse {

    // Mirror the AssetHolding column scales (amount 19,4 — quantity 19,8) so an empty summary
    // serializes with the same shape as a populated one (0.0000 / 0.00000000), not a bare 0.
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(4);

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(8);

    long holdingCount;

    BigDecimal averagePrice;

    BigDecimal quantitySum;

    BigDecimal amountSum;

    LocalDate periodStart;

    LocalDate periodEnd;

    public static HoldingSummaryResponse empty() {
        return new HoldingSummaryResponse(0, null, ZERO_QUANTITY, ZERO_AMOUNT, null, null);
    }

    public static HoldingSummaryResponse of(
            long holdingCount,
            BigDecimal averagePrice,
            BigDecimal quantitySum,
            BigDecimal amountSum,
            LocalDate periodStart,
            LocalDate periodEnd) {
        return new HoldingSummaryResponse(
                holdingCount, averagePrice, quantitySum, amountSum, periodStart, periodEnd);
    }
}
