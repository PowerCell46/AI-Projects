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

    long holdingCount;

    BigDecimal averagePrice;

    BigDecimal quantitySum;

    BigDecimal amountSum;

    LocalDate periodStart;

    LocalDate periodEnd;

    public static HoldingSummaryResponse empty() {
        return new HoldingSummaryResponse(0, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
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
