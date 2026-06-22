package com.wealthbuilder.backend.utils;

import java.util.Locale;


/**
 * Turns an asset name into a URL slug, mirroring the frontend's {@code slugify} exactly so the
 * two sides agree: lowercased, every run of non-alphanumeric characters collapsed to a single
 * dash, leading/trailing dashes trimmed. "Precious Metals" becomes "precious-metals".
 */
public final class Slug {

    private Slug() {
    }

    public static String of(String value) {
        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
