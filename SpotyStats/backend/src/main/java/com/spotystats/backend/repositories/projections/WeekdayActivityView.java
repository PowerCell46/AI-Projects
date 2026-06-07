package com.spotystats.backend.repositories.projections;

/**
 * Listening aggregate for one ISO weekday (1 = Monday … 7 = Sunday, in the
 * caller's time zone). Weekdays without plays are absent.
 */
public interface WeekdayActivityView {

    int getIsoWeekday();

    long getPlays();

    long getListeningTimeMs();
}
