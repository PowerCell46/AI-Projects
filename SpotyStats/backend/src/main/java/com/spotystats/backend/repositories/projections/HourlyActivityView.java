package com.spotystats.backend.repositories.projections;

/**
 * Play count for one hour of the day (0-23, in the caller's time zone).
 * Hours without plays are absent.
 */
public interface HourlyActivityView {

    int getHour();

    long getPlays();
}
