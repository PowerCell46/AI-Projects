package com.wealthbuilder.backend.repositories.projections;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * One-row aggregate over a filtered set of a user's holdings for a single asset: the row count,
 * the summed amount and quantity, and the earliest/latest purchase date. Computed in SQL so the
 * summary never materialises the underlying holdings. When nothing matches, {@code holdingCount}
 * is zero, the sums coalesce to zero, and the period bounds are null.
 */
public interface HoldingAggregate {

    long getHoldingCount();

    BigDecimal getAmountSum();

    BigDecimal getQuantitySum();

    LocalDate getPeriodStart();

    LocalDate getPeriodEnd();
}
