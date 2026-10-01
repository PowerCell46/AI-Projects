package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowEmailRendererTest {

    private static final String FEED_URL = "http://localhost:5173/feed";

    private final FollowEmailRenderer renderer = new FollowEmailRenderer("http://localhost:5173");

    @Test
    void should_carry_the_greeting_follower_line_and_feed_link_in_the_html_part() {
        String html = renderer.render("ana", "bob").getHtml();

        assertFalse(html.contains("{{"));
        assertTrue(html.contains("Hi bob,"));
        assertTrue(html.contains("ana (@ana) is now following you."));
        assertTrue(html.contains("href=\"" + FEED_URL + "\""));
    }

    @Test
    void should_carry_the_greeting_follower_line_and_feed_link_in_the_text_part() {
        String text = renderer.render("ana", "bob").getText();

        assertFalse(text.contains("{{"));
        assertTrue(text.contains("Hi bob,"));
        assertTrue(text.contains("ana (@ana) is now following you."));
        assertTrue(text.contains(FEED_URL));
    }

    @Test
    void should_have_an_open_twitter_button_in_the_html_part() {
        String html = renderer.render("ana", "bob").getHtml();

        assertTrue(html.contains(">Open Twitter</a>"));
    }

    @Test
    void should_greet_the_followee_and_name_the_follower_not_the_other_way_round() {
        String text = renderer.render("ana", "bob").getText();

        assertFalse(text.contains("Hi ana,"));
        assertFalse(text.contains("bob (@bob)"));
    }

    @Test
    void should_build_the_feed_link_from_the_configured_base_url() {
        FollowEmailRenderer productionRenderer = new FollowEmailRenderer("https://twitter.example.com");

        RenderedEmail rendered = productionRenderer.render("ana", "bob");

        assertTrue(rendered.getHtml().contains("href=\"https://twitter.example.com/feed\""));
        assertTrue(rendered.getText().contains("https://twitter.example.com/feed"));
    }

    @Test
    void should_escape_markup_in_a_username_for_the_html_part_only() {
        RenderedEmail rendered = renderer.render("<b>ana</b>", "bob");

        assertFalse(rendered.getHtml().contains("<b>ana</b>"));
        assertTrue(rendered.getHtml().contains("&lt;b&gt;ana&lt;/b&gt; (@&lt;b&gt;ana&lt;/b&gt;)"));
        assertTrue(rendered.getText().contains("<b>ana</b> (@<b>ana</b>)"));
    }
}
