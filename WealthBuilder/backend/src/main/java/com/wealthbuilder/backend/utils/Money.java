package com.wealthbuilder.backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * Shared money/quantity arithmetic so the derived unit price is computed identically wherever
 * it appears (holding rows and the aggregation average).
 */
public final class Money {

    public static final int PRICE_SCALE = 8;

    private Money() {
    }

    /**
     * Unit price = total spent / units, rounded half-up to {@link #PRICE_SCALE} decimals.
     */
    public static BigDecimal unitPrice(BigDecimal boughtForAmount, BigDecimal quantity) {
        return boughtForAmount.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
