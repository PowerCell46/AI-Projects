package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import lombok.Getter;

public enum NewsSchedule {

    DAILY(1, EnumSet.allOf(DayOfWeek.class)),

    WEEKDAYS(2, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),

    WEEKENDS(3, EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),

    WEEKLY(4, EnumSet.of(DayOfWeek.MONDAY));

    /**
     * The value stored in the database. Fixed per constant, so reordering or inserting constants
     * never changes what existing rows mean.
     */
    @Getter
    private final int code;

    private final Set<DayOfWeek> runDays;

    NewsSchedule(int code, Set<DayOfWeek> runDays) {
        this.code = code;
        this.runDays = runDays;
    }

    public static NewsSchedule fromCode(int code) {
        for (NewsSchedule schedule : values()) {
            if (schedule.code == code) {
                return schedule;
            }
        }

        throw new IllegalArgumentException("No news schedule has code %d.".formatted(code));
    }

    public static Set<NewsSchedule> runningOn(DayOfWeek day) {
        EnumSet<NewsSchedule> schedules = EnumSet.noneOf(NewsSchedule.class);

        for (NewsSchedule schedule : values()) {
            if (schedule.runsOn(day)) {
                schedules.add(schedule);
            }
        }

        return schedules;
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
