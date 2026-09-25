package com.peter_gerdzhikov.signal_flow_mail_service.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlToPlainTextConverterTest {

    @Test
    void p_tags_become_paragraphs_separated_by_a_blank_line() {
        String text = HtmlToPlainTextConverter.convert("<p>First.</p><p>Second.</p>");

        assertEquals("First.\n\nSecond.", text);
    }

    @Test
    void li_tags_become_dash_prefixed_items() {
        String text = HtmlToPlainTextConverter.convert("<ul><li>One</li><li>Two</li></ul>");

        assertEquals("- One\n- Two", text);
    }

    @Test
    void a_tag_becomes_its_text_followed_by_the_href_in_parentheses() {
        String text = HtmlToPlainTextConverter.convert("<p>See <a href=\"https://example.com/notes\">release notes</a>.</p>");

        assertEquals("See release notes (https://example.com/notes).", text);
    }

    @Test
    void strong_and_em_tags_are_unwrapped_to_their_text() {
        String text = HtmlToPlainTextConverter.convert("<p><strong>Bold</strong> and <em>italic</em>.</p>");

        assertEquals("Bold and italic.", text);
    }

    @Test
    void html_entities_are_decoded() {
        String text = HtmlToPlainTextConverter.convert("<p>Tom &amp; Jerry &mdash; 100&#37; fun.</p>");

        assertEquals("Tom & Jerry — 100% fun.", text);
    }

    @Test
    void nested_lists_are_indented_one_level_deeper_than_their_parent() {
        String text = HtmlToPlainTextConverter.convert("<ul><li>Outer<ul><li>Inner</li></ul></li></ul>");

        assertTrue(text.contains("- Outer"));
        assertTrue(text.contains("  - Inner"));
    }

    @Test
    void null_input_returns_an_empty_string() {
        assertEquals("", HtmlToPlainTextConverter.convert(null));
    }

    @Test
    void blank_input_returns_an_empty_string() {
        assertEquals("", HtmlToPlainTextConverter.convert("   "));
    }
}
