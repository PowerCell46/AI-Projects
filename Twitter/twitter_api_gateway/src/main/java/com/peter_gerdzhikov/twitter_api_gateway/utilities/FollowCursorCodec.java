package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;

public final class FollowCursorCodec {

    private static final String SEPARATOR = ":";

    private static final Pattern MICROS_PATTERN = Pattern.compile("\\d{1,18}");

    private FollowCursorCodec() {
    }

    public static String encode(Instant createdAt, UUID id) {
        long epochMicros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
        String raw = epochMicros + SEPARATOR + id;

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Accepts only what {@link #encode} produces: the decoded cursor is re-encoded and must equal the input,
     * so padding, signs, leading zeros and upper-case ids are all rejected.
     */
    public static FollowCursor decode(String cursor) {
        try {
            FollowCursor decoded = parse(cursor);
            if (!encode(decoded.getCreatedAt(), decoded.getId()).equals(cursor)) {
                throw new InvalidCursorException();
            }

            return decoded;
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }

    private static FollowCursor parse(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split(SEPARATOR, -1);
        if (parts.length != 2 || !MICROS_PATTERN.matcher(parts[0]).matches()) {
            throw new InvalidCursorException();
        }

        Instant createdAt = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);

        return new FollowCursor(UUID.fromString(parts[1]), createdAt);
    }
}
