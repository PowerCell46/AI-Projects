package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

/**
 * Strips CR/LF from user-supplied values before they reach a log line, so a caller cannot forge
 * fabricated log entries by embedding a newline in a field like a category or topic name.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        return value.replaceAll("[\r\n]", "_");
    }
}
