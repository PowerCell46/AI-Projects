package com.wealthbuilder.backend.DTOs.dashboard;

import com.wealthbuilder.backend.repositories.projections.AssetInvestment;
import lombok.Value;

import java.math.BigDecimal;


/**
 * One slice of the home donut: how much the current user has invested in a single asset.
 */
@Value
public class AssetDistributionResponse {

    Long assetId;

    String assetName;

    BigDecimal amountInvested;

    public static AssetDistributionResponse from(AssetInvestment investment) {
        return new AssetDistributionResponse(
                investment.getAssetId(),
                investment.getAssetName(),
                investment.getTotalInvested());
    }
}
