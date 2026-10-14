package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewsHeadlinesTest {

    @Test
    void should_return_headlines_in_document_order_when_items_have_the_required_shape() {
        String html = "<ul><li><strong>First</strong> Detail. <a href=\"https://a.example\">A</a></li>"
                + "<li><strong>Second</strong> Detail. <a href=\"https://b.example\">B</a></li>"
                + "<li><strong>Third</strong> Detail. <a href=\"https://c.example\">C</a></li></ul>";

        assertThat(NewsHeadlines.extract(html)).containsExactly("First", "Second", "Third");
    }

    @Test
    void should_unescape_entities_when_items_are_spread_over_indented_lines() {
        String html = """
                <ul>
                    <li>
                        <strong>Apple&#39;s fall lineup &amp; prices</strong> Detail.
                    </li>
                    <li>
                        <strong>U.S. stocks rallied on Friday</strong> Detail.
                    </li>
                </ul>""";

        assertThat(NewsHeadlines.extract(html))
                .containsExactly("Apple's fall lineup & prices", "U.S. stocks rallied on Friday");
    }

    @Test
    void should_unescape_a_typographic_apostrophe_when_it_is_an_entity() {
        String html = "<ul><li><strong>Apple&rsquo;s fall lineup</strong> Detail.</li></ul>";

        assertThat(NewsHeadlines.extract(html)).containsExactly("Apple’s fall lineup");
    }

    @Test
    void should_strip_nested_tags_when_the_headline_holds_one() {
        String html = "<ul><li><strong>Rust <em>1.90</em> released</strong> Detail.</li></ul>";

        assertThat(NewsHeadlines.extract(html)).containsExactly("Rust 1.90 released");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<UL><LI><STRONG>Upper</STRONG> Detail.</LI></UL>",
            "<ul><li class=\"x\"><strong>Upper</strong> Detail.</li></ul>",
            "<ul><li><strong class=\"y\">Upper</strong> Detail.</li></ul>"
    })
    void should_match_the_item_when_case_or_attributes_differ(String html) {
        assertThat(NewsHeadlines.extract(html)).hasSize(1);
    }

    @Test
    void should_collapse_a_line_break_to_one_space_when_it_is_inside_a_headline() {
        String html = "<ul><li><strong>Two\n   lines</strong> Detail.</li></ul>";

        assertThat(NewsHeadlines.extract(html)).containsExactly("Two lines");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<ul><li>No bold here. <strong>Late bold</strong></li></ul>",
            "<p><strong>Outside any item</strong></p>",
            "<ul><li><strong></strong> Empty.</li></ul>",
            "<ul><li><strong>  </strong> Blank.</li></ul>"
    })
    void should_ignore_the_item_when_it_has_no_leading_headline(String html) {
        assertThat(NewsHeadlines.extract(html)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>Nothing new worth reporting today.</p>",
            "",
            "   "
    })
    void should_return_an_empty_list_when_there_is_no_item(String html) {
        assertThat(NewsHeadlines.extract(html)).isEmpty();
    }
}
