package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    private static final String VALID_HTML = "classpath:templates/validTemplate.html";

    private static final String VALID_TEXT = "classpath:templates/validTemplate.txt";

    private static final Set<String> TOKENS = Set.of("NAME", "LINK");

    @Nested
    class Constructor {

        @ParameterizedTest
        @CsvSource({
                "classpath:templates/templateWithUnknownToken.html, classpath:templates/validTemplate.txt, NOT_A_REAL_TOKEN",
                "classpath:templates/validTemplate.html, classpath:templates/templateWithUnknownToken.txt, NOT_A_REAL_TOKEN",
                "classpath:templates/templateWithMissingToken.html, classpath:templates/validTemplate.txt, LINK",
                "classpath:templates/validTemplate.html, classpath:templates/templateWithMissingToken.txt, LINK"
        })
        void should_fail_at_load_when_either_template_has_an_unknown_or_missing_token(
                String htmlPath, String textPath, String offendingToken
        ) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class, () -> new EmailTemplate(htmlPath, textPath, TOKENS));

            assertTrue(exception.getMessage().contains(offendingToken));
        }

        @Test
        void should_fail_at_load_when_a_template_file_does_not_exist() {
            assertThrows(IllegalStateException.class,
                    () -> new EmailTemplate("classpath:templates/doesNotExist.html", VALID_TEXT, TOKENS));
        }
    }

    @Nested
    class Render {

        private final EmailTemplate emailTemplate = new EmailTemplate(VALID_HTML, VALID_TEXT, TOKENS);

        @Test
        void should_escape_angle_brackets_ampersands_and_quotes_in_the_html_part() {
            RenderedEmail rendered = emailTemplate.render(Map.of("NAME", "<b>&\"x\"", "LINK", "https://example.com"));

            assertTrue(rendered.getHtml().contains("&lt;b&gt;&amp;&quot;x&quot; https://example.com"));
        }

        @Test
        void should_leave_values_unescaped_in_the_text_part() {
            RenderedEmail rendered = emailTemplate.render(Map.of("NAME", "<b>&\"x\"", "LINK", "https://example.com"));

            assertEquals("<b>&\"x\" https://example.com\n", rendered.getText());
        }

        @Test
        void should_strip_line_breaks_from_values_in_the_text_part() {
            RenderedEmail rendered = emailTemplate.render(Map.of("NAME", "a\r\nb\nc", "LINK", "https://example.com"));

            assertEquals("abc https://example.com\n", rendered.getText());
        }

        @Test
        void should_keep_a_value_that_looks_like_a_token_literal_in_both_parts() {
            RenderedEmail rendered = emailTemplate.render(Map.of("NAME", "{{LINK}}", "LINK", "https://example.com"));

            assertTrue(rendered.getHtml().contains("{{LINK}} https://example.com"));
            assertEquals("{{LINK}} https://example.com\n", rendered.getText());
        }

        @Test
        void should_keep_dollar_signs_and_backslashes_in_values_literal() {
            RenderedEmail rendered = emailTemplate.render(Map.of("NAME", "$1\\n", "LINK", "https://example.com"));

            assertEquals("$1\\n https://example.com\n", rendered.getText());
        }

        @Test
        void should_reject_values_that_do_not_match_the_declared_tokens() {
            assertThrows(IllegalArgumentException.class, () -> emailTemplate.render(Map.of("NAME", "ana")));
        }
    }
}
