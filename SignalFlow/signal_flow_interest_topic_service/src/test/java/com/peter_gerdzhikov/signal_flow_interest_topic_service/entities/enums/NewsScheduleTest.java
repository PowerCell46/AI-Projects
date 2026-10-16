package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NewsScheduleTest {

    private static final DayOfWeek MON = DayOfWeek.MONDAY;

    private static final DayOfWeek TUE = DayOfWeek.TUESDAY;

    private static final DayOfWeek WED = DayOfWeek.WEDNESDAY;

    private static final DayOfWeek THU = DayOfWeek.THURSDAY;

    private static final DayOfWeek FRI = DayOfWeek.FRIDAY;

    private static final DayOfWeek SAT = DayOfWeek.SATURDAY;

    private static final DayOfWeek SUN = DayOfWeek.SUNDAY;

    @ParameterizedTest
    @MethodSource("runDaysByPreset")
    void should_run_only_on_its_days_when_checking_every_day_of_the_week(
            NewsSchedule schedule, DayOfWeek[] expectedRunDays) {
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean expected = Arrays.asList(expectedRunDays).contains(day);

            assertThat(schedule.runsOn(day))
                    .as("%s runs on %s", schedule, day)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @MethodSource("schedulesByDay")
    void should_return_every_preset_that_runs_when_asking_for_a_day(
            DayOfWeek day, NewsSchedule[] expectedSchedules) {
        assertThat(NewsSchedule.runningOn(day)).containsExactlyInAnyOrder(expectedSchedules);
    }

    @ParameterizedTest
    @MethodSource("periodStarts")
    void should_start_the_period_after_the_previous_run_day_when_the_preset_runs_on_the_news_date(
            NewsSchedule schedule, LocalDate newsDate, LocalDate expectedPeriodStart) {
        assertThat(schedule.runsOn(newsDate.getDayOfWeek())).isTrue();

        assertThat(schedule.periodStart(newsDate)).isEqualTo(expectedPeriodStart);
    }

    @ParameterizedTest
    @MethodSource("codesByPreset")
    void should_keep_its_fixed_code_when_asking_a_preset_for_its_code(NewsSchedule schedule, int expectedCode) {
        assertThat(schedule.getCode()).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @EnumSource(NewsSchedule.class)
    void should_find_the_preset_when_looking_up_its_own_code(NewsSchedule schedule) {
        assertThat(NewsSchedule.fromCode(schedule.getCode())).isEqualTo(schedule);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5})
    void should_reject_the_code_when_no_preset_has_it(int unknownCode) {
        assertThatThrownBy(() -> NewsSchedule.fromCode(unknownCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No news schedule has code %d.".formatted(unknownCode));
    }

    private static Stream<Arguments> codesByPreset() {
        return Stream.of(
                Arguments.of(NewsSchedule.DAILY, 1),
                Arguments.of(NewsSchedule.WEEKDAYS, 2),
                Arguments.of(NewsSchedule.WEEKENDS, 3),
                Arguments.of(NewsSchedule.WEEKLY, 4));
    }

    private static Stream<Arguments> runDaysByPreset() {
        return Stream.of(
                Arguments.of(NewsSchedule.DAILY, new DayOfWeek[]{MON, TUE, WED, THU, FRI, SAT, SUN}),
                Arguments.of(NewsSchedule.WEEKDAYS, new DayOfWeek[]{MON, TUE, WED, THU, FRI}),
                Arguments.of(NewsSchedule.WEEKENDS, new DayOfWeek[]{SAT, SUN}),
                Arguments.of(NewsSchedule.WEEKLY, new DayOfWeek[]{MON}));
    }

    private static Stream<Arguments> schedulesByDay() {
        return Stream.of(
                Arguments.of(MON, new NewsSchedule[]{NewsSchedule.DAILY, NewsSchedule.WEEKDAYS, NewsSchedule.WEEKLY}),
                Arguments.of(TUE, new NewsSchedule[]{NewsSchedule.DAILY, NewsSchedule.WEEKDAYS}),
                Arguments.of(FRI, new NewsSchedule[]{NewsSchedule.DAILY, NewsSchedule.WEEKDAYS}),
                Arguments.of(SAT, new NewsSchedule[]{NewsSchedule.DAILY, NewsSchedule.WEEKENDS}),
                Arguments.of(SUN, new NewsSchedule[]{NewsSchedule.DAILY, NewsSchedule.WEEKENDS}));
    }

    private static Stream<Arguments> periodStarts() {
        return Stream.of(
                Arguments.of(NewsSchedule.DAILY, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13)),
                Arguments.of(NewsSchedule.DAILY, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 10)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)),
                Arguments.of(NewsSchedule.WEEKENDS, LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 12)),
                Arguments.of(NewsSchedule.WEEKENDS, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18)),
                Arguments.of(NewsSchedule.WEEKLY, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 6)),
                Arguments.of(NewsSchedule.WEEKLY, LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 29)),
                Arguments.of(NewsSchedule.WEEKDAYS, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 2)),
                Arguments.of(NewsSchedule.WEEKENDS, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 9, 28)));
    }
}
