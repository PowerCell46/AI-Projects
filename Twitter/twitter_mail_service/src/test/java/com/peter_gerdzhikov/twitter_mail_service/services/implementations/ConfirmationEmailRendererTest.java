package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationEmailRendererTest {

    private static final String URL = "http://localhost:5173/confirm?token=abc-DEF_123";

    private static final Instant EXPIRES_AT = Instant.parse("2026-10-02T12:00:00Z");

    private static final String IGNORE_LINE = "If you didn't sign up, ignore this email.";

    private final ConfirmationEmailRenderer renderer = new ConfirmationEmailRenderer("Europe/Sofia");

    @Test
    void should_carry_the_url_username_expiry_and_ignore_line_in_the_html_part() {
        String html = renderer.render("ana_k", URL, EXPIRES_AT).getHtml();

        assertFalse(html.contains("{{"));
        assertTrue(html.contains("Hi ana_k,"));
        assertTrue(html.contains("href=\"" + URL + "\""));
        assertTrue(html.contains(">" + URL + "</a>"));
        assertTrue(html.contains("This link expires on 2 October 2026, 15:00 EEST."));
        assertTrue(html.contains(IGNORE_LINE));
    }

    @Test
    void should_carry_the_url_username_expiry_and_ignore_line_in_the_text_part() {
        String text = renderer.render("ana_k", URL, EXPIRES_AT).getText();

        assertFalse(text.contains("{{"));
        assertTrue(text.contains("Hi ana_k,"));
        assertTrue(text.contains(URL));
        assertTrue(text.contains("This link expires on 2 October 2026, 15:00 EEST."));
        assertTrue(text.contains(IGNORE_LINE));
    }

    @Test
    void should_have_a_confirm_email_button_in_the_html_part() {
        String html = renderer.render("ana_k", URL, EXPIRES_AT).getHtml();

        assertTrue(html.contains(">Confirm email</a>"));
    }

    @Test
    void should_escape_ampersands_in_the_url_for_the_html_part_only() {
        String urlWithAmpersand = "http://localhost:5173/confirm?token=a&b=c";

        RenderedEmail rendered = renderer.render("ana_k", urlWithAmpersand, EXPIRES_AT);

        assertTrue(rendered.getHtml().contains("href=\"http://localhost:5173/confirm?token=a&amp;b=c\""));
        assertTrue(rendered.getText().contains(urlWithAmpersand));
    }

    @Test
    void should_not_let_a_url_break_out_of_the_href_attribute_or_inject_markup() {
        String hostileUrl = "http://x/\" onmouseover=\"alert(1)\"><script>alert(2)</script>";

        String html = renderer.render("ana_k", hostileUrl, EXPIRES_AT).getHtml();

        assertFalse(html.contains("\" onmouseover"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("href=\"http://x/&quot; onmouseover=&quot;alert(1)&quot;&gt;&lt;script&gt;alert(2)&lt;/script&gt;\""));
    }

    @Test
    void should_strip_line_breaks_from_a_url_in_the_text_part() {
        String text = renderer.render("ana_k", "http://x/a\r\nBcc: victim@example.com", EXPIRES_AT).getText();

        assertTrue(text.contains("http://x/aBcc: victim@example.com"));
        assertFalse(text.contains("http://x/a\r"));
    }

    @Test
    void should_format_the_expiry_in_the_configured_zone() {
        ConfirmationEmailRenderer newYorkRenderer = new ConfirmationEmailRenderer("America/New_York");

        String text = newYorkRenderer.render("ana_k", URL, Instant.parse("2026-12-02T12:00:00Z")).getText();

        assertTrue(text.contains("This link expires on 2 December 2026, 07:00 EST."));
    }

    @Test
    void should_fail_to_construct_when_the_zone_is_unknown() {
        assertThrows(RuntimeException.class, () -> new ConfirmationEmailRenderer("Not/AZone"));
    }
}
