package com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event;

import lombok.Getter;

/**
 * Hand-copied wire contract of the topic service's {@code NewsSchedule} - no shared library between the
 * services. Only the email label lives here; the days each preset runs on are the topic service's concern.
 */
@Getter
public enum NewsSchedule {

    DAILY(null),

    WEEKDAYS("Weekdays"),

    WEEKENDS("Weekends"),

    WEEKLY("Weekly");

    private final String emailLabel;

    NewsSchedule(String emailLabel) {
        this.emailLabel = emailLabel;
    }
}
