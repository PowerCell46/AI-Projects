package com.peter_gerdzhikov.signal_flow_mail_service.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkHostAnnotatorTest {

    @Test
    void a_link_is_followed_by_its_host_in_parentheses() {
        String html = LinkHostAnnotator.annotate("<p>See <a href=\"https://example.com/notes?a=1\">release notes</a>.</p>");

        assertEquals("<p>See <a href=\"https://example.com/notes?a=1\">release notes</a> (example.com).</p>", html);
    }

    @Test
    void every_link_gets_its_own_host() {
        String html = LinkHostAnnotator.annotate("<a href=\"https://a.example\">A</a><a href=\"https://b.example\">B</a>");

        assertEquals("<a href=\"https://a.example\">A</a> (a.example)<a href=\"https://b.example\">B</a> (b.example)", html);
    }

    @Test
    void a_unicode_host_is_shown_in_its_punycode_form() {
        String html = LinkHostAnnotator.annotate("<a href=\"https://b\u00fccher.example/\">Books</a>");

        assertEquals("<a href=\"https://b\u00fccher.example/\">Books</a> (xn--bcher-kva.example)", html);
    }

    @Test
    void a_link_without_a_readable_host_becomes_its_plain_text() {
        String html = LinkHostAnnotator.annotate("<p><a href=\"https://exa mple.com\">Odd</a> and <a href=\"https:///path\">None</a></p>");

        assertEquals("<p>Odd and None</p>", html);
    }

    @Test
    void markup_without_links_is_returned_unchanged() {
        assertEquals("<p>Plain <strong>text</strong></p>", LinkHostAnnotator.annotate("<p>Plain <strong>text</strong></p>"));
    }

    @Test
    void blank_or_null_input_becomes_an_empty_string() {
        assertEquals("", LinkHostAnnotator.annotate(null));
        assertEquals("", LinkHostAnnotator.annotate("  "));
    }
}
