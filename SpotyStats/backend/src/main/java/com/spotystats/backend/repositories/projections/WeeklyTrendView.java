package com.spotystats.backend.repositories.projections;

import java.time.LocalDate;


/**
 * Listening aggregate for one calendar week (Monday start, in the caller's
 * time zone). Weeks without plays are absent.
 */
public interface WeeklyTrendView {

    LocalDate getWeekStart();

    long getPlays();

    long getListeningTimeMs();
}
