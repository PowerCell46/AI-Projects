package com.wealthbuilder.backend.exceptions.holding;

import java.time.LocalDate;


/**
 * Raised when a holdings filter supplies a {@code from} date later than its {@code to} date —
 * an impossible range. Surfaced as HTTP 400 by the global exception handler so the client gets a
 * clear bad-request rather than a silently empty result.
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        super("The 'from' date (" + from + ") must not be after the 'to' date (" + to + ").");
    }
}
