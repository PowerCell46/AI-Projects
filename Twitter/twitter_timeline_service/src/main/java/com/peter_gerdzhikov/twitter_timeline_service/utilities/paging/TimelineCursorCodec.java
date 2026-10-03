package com.peter_gerdzhikov.twitter_timeline_service.utilities.paging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;

/**
 * The opaque keyset cursor of every list here: a timestamp and a tweet id, which the list is ordered by.
 */
public final class TimelineCursorCodec {

    private static final String SEPARATOR = ":";

    private static final Pattern MICROS_PATTERN = Pattern.compile("\\d{1,18}");

    private TimelineCursorCodec() {
    }

    public static String encode(Instant timestamp, UUID tweetId) {
        long epochMicros = ChronoUnit.MICROS.between(Instant.EPOCH, timestamp);
        String raw = epochMicros + SEPARATOR + tweetId;

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Accepts only what {@link #encode} produces: the decoded cursor is re-encoded and must equal the input,
     * so padding, signs, leading zeros and upper-case ids are all rejected.
     */
    public static TimelineCursor decode(String cursor) {
        try {
            TimelineCursor decoded = parse(cursor);
            if (!encode(decoded.getTimestamp(), decoded.getTweetId()).equals(cursor)) {
                throw new InvalidCursorException();
            }

            return decoded;

        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }

    private static TimelineCursor parse(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split(SEPARATOR, -1);
        if (parts.length != 2 || !MICROS_PATTERN.matcher(parts[0]).matches()) {
            throw new InvalidCursorException();
        }

        Instant timestamp = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);

        return new TimelineCursor(UUID.fromString(parts[1]), timestamp);
    }
}
