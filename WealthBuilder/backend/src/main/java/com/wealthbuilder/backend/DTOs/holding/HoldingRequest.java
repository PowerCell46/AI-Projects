package com.wealthbuilder.backend.DTOs.holding;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * Create/edit payload for a holding. The {@code @Digits} bounds mirror the entity's column
 * precision so over-precise values are rejected before they reach persistence. Price is never
 * accepted — it is derived from amount and quantity.
 */
@Getter
@Setter
@NoArgsConstructor
public class HoldingRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotNull
    @Positive
    @Digits(integer = 15, fraction = 4)
    private BigDecimal boughtForAmount;

    @NotBlank
    @Size(max = 30)
    private String unit;

    @NotNull
    @Positive
    @Digits(integer = 11, fraction = 8)
    private BigDecimal quantity;

    @NotNull
    @PastOrPresent
    private LocalDate date;

    @Size(max = 1000)
    private String note;
}
