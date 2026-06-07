package com.spotystats.backend.utilities;

import java.time.ZoneId;


/**
 * Parses caller-supplied time zone names, falling back to UTC on anything
 * invalid — a wrong zone should skew timestamps, not fail the request.
 */
public final class ZoneIdParser {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private ZoneIdParser() {
    }

    public static ZoneId parseOrUtc(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (Exception invalidZone) {
            return UTC;
        }
    }
}
