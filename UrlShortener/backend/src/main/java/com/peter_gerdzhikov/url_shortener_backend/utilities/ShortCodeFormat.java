package com.peter_gerdzhikov.url_shortener_backend.utilities;

import java.util.regex.Pattern;

/**
 * Every short code the application generates is a {@link Base62Encoder} output, so its charset is
 * fixed. A single path segment outside that charset (a dot, a slash-free filename, punctuation) is
 * never a code this app issued — it's most often a vulnerability scanner probing for files like
 * {@code .env} or {@code wp-config.php} — and can be rejected before touching the database.
 */
public final class ShortCodeFormat {

    private static final Pattern VALID_CODE = Pattern.compile("[0-9A-Za-z]+");

    private ShortCodeFormat() {
    }

    public static boolean isValid(String code) {
        return code != null && VALID_CODE.matcher(code).matches();
    }
}
