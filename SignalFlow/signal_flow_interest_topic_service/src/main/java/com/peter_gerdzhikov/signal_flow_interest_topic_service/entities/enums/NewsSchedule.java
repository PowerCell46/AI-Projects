package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public enum NewsSchedule {

    DAILY(EnumSet.allOf(DayOfWeek.class)),

    WEEKDAYS(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),

    WEEKENDS(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),

    WEEKLY(EnumSet.of(DayOfWeek.MONDAY));

    private final Set<DayOfWeek> runDays;

    NewsSchedule(Set<DayOfWeek> runDays) {
        this.runDays = runDays;
    }

    public boolean runsOn(DayOfWeek day) {
        return runDays.contains(day);
    }

    public LocalDate periodStart(LocalDate newsDate) {
        LocalDate previousRunDate = newsDate.minusDays(1);

        while (!runsOn(previousRunDate.getDayOfWeek())) {
            previousRunDate = previousRunDate.minusDays(1);
        }

        return previousRunDate.plusDays(1);
    }
}
