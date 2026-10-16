package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

class NewsScheduleConverterTest {

    private final NewsScheduleConverter converter = new NewsScheduleConverter();

    @ParameterizedTest
    @EnumSource(NewsSchedule.class)
    void should_return_the_same_preset_when_converting_to_the_column_and_back(NewsSchedule schedule) {
        Integer code = converter.convertToDatabaseColumn(schedule);

        assertThat(converter.convertToEntityAttribute(code)).isEqualTo(schedule);
    }

    @Test
    void should_return_null_when_converting_a_null_preset() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void should_return_null_when_converting_a_null_code() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void should_reject_the_code_when_no_preset_has_it() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
