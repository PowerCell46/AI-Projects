package com.wealthbuilder.backend.DTOs.holding;

import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.utils.Money;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;


/**
 * A holding as returned to the SPA, including the derived unit {@code price} and the audit
 * {@code createdAt} so rows can show both the purchase day and when the record was entered.
 */
@Value
public class HoldingResponse {

    Long id;

    Long assetId;

    String name;

    BigDecimal boughtForAmount;

    String unit;

    BigDecimal quantity;

    BigDecimal price;

    LocalDate date;

    String note;

    Instant createdAt;

    public static HoldingResponse from(AssetHolding holding) {
        return new HoldingResponse(
                holding.getId(),
                holding.getAsset().getId(),
                holding.getName(),
                holding.getBoughtForAmount(),
                holding.getUnit(),
                holding.getQuantity(),
                Money.unitPrice(holding.getBoughtForAmount(), holding.getQuantity()),
                holding.getDate(),
                holding.getNote(),
                holding.getCreatedAt());
    }
}
