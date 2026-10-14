package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

/**
 * Reads the headline of each item out of stored news HTML. The pattern matches the item shape that
 * {@code NewsGenerationServiceImpl}'s system prompt demands; if one changes, the other must too.
 */
public final class NewsHeadlines {

    private static final Pattern HEADLINE_PATTERN = Pattern.compile(
            "<li\\b[^>]*>\\s*<strong\\b[^>]*>(.*?)</strong>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern NESTED_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private NewsHeadlines() {
    }

    public static List<String> extract(String html) {
        List<String> headlines = new ArrayList<>();
        if (html == null) {
            return headlines;
        }

        Matcher matcher = HEADLINE_PATTERN.matcher(html);
        while (matcher.find()) {
            String headline = clean(matcher.group(1));
            if (!headline.isEmpty()) {
                headlines.add(headline);
            }
        }

        return headlines;
    }

    private static String clean(String rawHeadline) {
        String withoutTags = NESTED_TAG_PATTERN.matcher(rawHeadline).replaceAll("");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);

        return WHITESPACE_PATTERN
                .matcher(unescaped)
                .replaceAll(" ")
                .strip();
    }
}
