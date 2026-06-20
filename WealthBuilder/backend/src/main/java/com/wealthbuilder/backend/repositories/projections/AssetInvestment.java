package com.wealthbuilder.backend.repositories.projections;

import java.math.BigDecimal;


/**
 * Aggregated net invested in a single asset by one user, populated by the dashboard
 * distribution query via Spring Data's alias-based projection.
 */
public interface AssetInvestment {

    Long getAssetId();

    String getAssetName();

    BigDecimal getTotalInvested();
}
