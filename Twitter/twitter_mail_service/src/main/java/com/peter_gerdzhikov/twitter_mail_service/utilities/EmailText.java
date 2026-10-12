package com.peter_gerdzhikov.twitter_mail_service.utilities;

public class EmailText {

    private static final String LINE_BREAKS = "[\r\n]";

    private EmailText() {
    }

    /**
     * Strips carriage returns and line feeds, so the value can neither smuggle extra header-like
     * lines into the text part nor a second recipient into a header field.
     */
    public static String stripLineBreaks(String value) {
        return value.replaceAll(LINE_BREAKS, "");
    }
}
