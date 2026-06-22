package com.wealthbuilder.backend.DTOs.holding;

import com.wealthbuilder.backend.exceptions.holding.InvalidDateRangeException;
import lombok.Getter;

import java.time.LocalDate;


/**
 * Optional criteria for narrowing a holdings list: a case-insensitive name fragment and an
 * inclusive purchase-date range. Any field left null is simply not applied. A blank name is
 * normalised to null so an empty search box behaves like no filter at all.
 */
@Getter
public class HoldingFilter {

    private final String name;

    private final LocalDate from;

    private final LocalDate to;

    private HoldingFilter(String name, LocalDate from, LocalDate to) {
        this.name = name;
        this.from = from;
        this.to = to;
    }

    public static HoldingFilter of(String name, LocalDate from, LocalDate to) {
        requireOrderedRange(from, to);

        return new HoldingFilter(blankToNull(name), from, to);
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(from, to);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
