package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicNewsEmailRendererTest {

    private static final String DEFAULT_TEMPLATE_PATH = "classpath:templates/topicNewsEmailTemplate.html";

    private static final String ZONE = "Europe/Sofia";

    @Nested
    class Render {

        private final TopicNewsEmailRenderer renderer = new TopicNewsEmailRenderer(ZONE, DEFAULT_TEMPLATE_PATH);

        @Test
        void should_replace_every_token_when_rendering() {
            TopicNewsNotificationEventDTO event = anEvent(
                    "AI regulation", "Technology", "<p>Body</p>", "recipient@example.com");

            String html = renderer.render(event);

            assertFalse(html.contains("{{"));
            assertTrue(html.contains("AI regulation"));
            assertTrue(html.contains("Technology"));
            assertTrue(html.contains("<p>Body</p>"));
            assertTrue(html.contains("recipient@example.com"));
        }

        @Test
        void should_escape_topic_name_category_name_and_email_address_when_rendering() {
            TopicNewsNotificationEventDTO event = anEvent(
                    "<script>alert(1)</script>", "Tech & Science", "<p>Body</p>", "\"quoted\"@example.com");

            String html = renderer.render(event);

            assertFalse(html.contains("<script>alert(1)</script>"));
            assertTrue(html.contains("&lt;script&gt;"));
            assertTrue(html.contains("Tech &amp; Science"));
            assertTrue(html.contains("&quot;quoted&quot;@example.com"));
        }

        @Test
        void should_keep_allowlisted_tags_and_their_text_when_sanitizing_data() {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category",
                    "<p>Rust 1.90 <strong>shipped</strong> with <em>faster</em> builds.</p>"
                            + "<ul><li>See <a href=\"https://example.com/notes\">release notes</a>.</li></ul>",
                    "recipient@example.com");

            String html = renderer.render(event);

            assertTrue(html.contains("<p>Rust 1.90 <strong>shipped</strong> with <em>faster</em> builds.</p>"));
            assertTrue(html.contains("<ul><li>See <a"));
            assertTrue(html.contains("href=\"https://example.com/notes\""));
            assertTrue(html.contains("release notes</a>.</li></ul>"));
        }

        @Test
        void should_strip_script_img_iframe_style_and_on_star_attributes_when_sanitizing_data() {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category",
                    "<script>alert(1)</script>"
                            + "<img src=\"x\" onerror=\"alert(1)\">"
                            + "<iframe src=\"https://evil.example\"></iframe>"
                            + "<style>body { color: red; }</style>"
                            + "<p onclick=\"alert(1)\">Safe text</p>",
                    "recipient@example.com");

            String html = renderer.render(event);

            assertFalse(html.contains("<script"));
            assertFalse(html.contains("alert(1)"));
            assertFalse(html.contains("<img"));
            assertFalse(html.contains("<iframe"));
            assertFalse(html.contains("<style"));
            assertFalse(html.contains("color: red"));
            assertFalse(html.contains("onclick"));
            assertTrue(html.contains("<p>Safe text</p>"));
        }

        @Test
        void should_drop_non_https_hrefs_but_keep_https_links_with_rel_noopener_noreferrer_when_sanitizing_data() {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category",
                    "<a href=\"javascript:alert(1)\">bad</a>"
                            + "<a href=\"http://example.com\">insecure</a>"
                            + "<a href=\"https://example.com\">good</a>",
                    "recipient@example.com");

            String html = renderer.render(event);

            assertFalse(html.contains("javascript:"));
            assertFalse(html.contains("href=\"http://example.com\""));
            assertTrue(html.contains("href=\"https://example.com\""));
            assertTrue(html.contains("rel=\""));
            assertTrue(html.contains("noopener"));
            assertTrue(html.contains("noreferrer"));
        }

        @Test
        void should_balance_unclosed_tags_when_sanitizing_data() {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category",
                    "<p>Unclosed paragraph<ul><li>Unclosed item", "recipient@example.com");

            String html = renderer.render(event);

            assertTrue(html.contains("<p>Unclosed paragraph"));
            assertTrue(html.contains("<li>Unclosed item</li>"));
            assertTrue(html.contains("</ul>"));
            assertTrue(html.contains("</p>"));
        }

        @Test
        void should_not_resubstitute_a_token_looking_string_found_inside_an_inserted_value() {
            TopicNewsNotificationEventDTO event = anEvent(
                    "{{DATA}}", "before {{TOPIC_NAME}} after", "<p>Body</p>", "recipient@example.com");

            String html = renderer.render(event);

            assertTrue(html.contains("before {{TOPIC_NAME}} after"));
            assertTrue(html.contains("{{DATA}}"));
        }

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.signal_flow_mail_service.services.implementations.TopicNewsEmailRendererTest#generatedAtAcrossDstBoundary")
        void should_format_generated_at_in_configured_zone(Instant generatedAt, String expectedFormattedTime) {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category", "Body", "recipient@example.com");
            event.setGeneratedAt(generatedAt);

            String html = renderer.render(event);

            assertTrue(html.contains(expectedFormattedTime));
        }

        @Test
        void should_format_news_date_as_day_month_year() {
            TopicNewsNotificationEventDTO event = anEvent("Topic", "Category", "Body", "recipient@example.com");
            event.setNewsDate(LocalDate.of(2026, 1, 5));

            String html = renderer.render(event);

            assertTrue(html.contains("5 January 2026"));
        }
    }

    @Nested
    class Constructor {

        @Test
        void should_throw_when_the_template_is_missing() {
            assertThrows(IllegalStateException.class,
                    () -> new TopicNewsEmailRenderer(ZONE, "classpath:templates/does-not-exist.html"));
        }

        @Test
        void should_throw_when_the_template_contains_an_unknown_token() {
            assertThrows(IllegalStateException.class,
                    () -> new TopicNewsEmailRenderer(ZONE, "classpath:templates/templateWithUnknownToken.html"));
        }

        @Test
        void should_throw_when_the_template_is_missing_a_known_token() {
            assertThrows(IllegalStateException.class,
                    () -> new TopicNewsEmailRenderer(ZONE, "classpath:templates/templateMissingDataToken.html"));
        }
    }

    static Stream<Arguments> generatedAtAcrossDstBoundary() {
        return Stream.of(
                Arguments.of(Instant.parse("2026-03-29T00:30:00Z"), "02:30 EET"),
                Arguments.of(Instant.parse("2026-03-29T01:30:00Z"), "04:30 EEST"));
    }

    private static TopicNewsNotificationEventDTO anEvent(String topicName, String categoryName, String data, String emailAddress) {
        return new TopicNewsNotificationEventDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                topicName,
                categoryName,
                LocalDate.of(2026, 9, 24),
                data,
                Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(),
                emailAddress);
    }
}
