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
     * Unit price = total spent / units, rounded half-up to {@link #PRICE_SCALE} decimals. A zero
     * quantity has no meaningful unit price, so it yields zero rather than letting {@code divide}
     * throw — keeping callers safe against any zero-quantity row regardless of how it was stored.
     */
    public static BigDecimal unitPrice(BigDecimal boughtForAmount, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }

        return boughtForAmount.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
