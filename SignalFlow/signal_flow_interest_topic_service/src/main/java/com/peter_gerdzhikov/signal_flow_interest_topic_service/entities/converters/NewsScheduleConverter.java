package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

@Converter(autoApply = true)
public class NewsScheduleConverter implements AttributeConverter<NewsSchedule, Integer> {

    @Override
    public Integer convertToDatabaseColumn(NewsSchedule schedule) {
        return schedule == null ? null : schedule.getCode();
    }

    @Override
    public NewsSchedule convertToEntityAttribute(Integer code) {
        return code == null ? null : NewsSchedule.fromCode(code);
    }
}
