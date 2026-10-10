package com.peter_gerdzhikov.twitter_mail_service.services.implementations.follow;

import com.peter_gerdzhikov.twitter_mail_service.services.implementations.RenderedEmail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
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

    @ParameterizedTest
    @ValueSource(strings = {"https://twitter.example.com/", "https://twitter.example.com//"})
    void should_not_double_the_slash_in_the_feed_link_when_the_base_url_ends_with_one(String baseUrl) {
        FollowEmailRenderer slashRenderer = new FollowEmailRenderer(baseUrl);

        RenderedEmail rendered = slashRenderer.render("ana", "bob");

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

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost:5173", "twitter.example.com", "ftp://twitter.example.com"})
    void should_fail_to_start_when_the_base_url_is_not_an_absolute_http_url(String baseUrl) {
        assertThrows(IllegalStateException.class, () -> new FollowEmailRenderer(baseUrl));
    }

    @Test
    void should_warn_when_the_base_url_is_plain_http_on_another_host(CapturedOutput output) {
        new FollowEmailRenderer("http://twitter.example.com");

        assertTrue(output.getOut().contains("app.mail.app-base-url is plain http outside localhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://twitter.example.com", "http://localhost:5173"})
    void should_not_warn_when_the_base_url_is_https_or_localhost(String baseUrl, CapturedOutput output) {
        new FollowEmailRenderer(baseUrl);

        assertFalse(output.getOut().contains("plain http"));
    }
}
