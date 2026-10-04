package com.peter_gerdzhikov.twitter_mail_service.services.implementations.follow;

import com.peter_gerdzhikov.twitter_mail_service.services.implementations.EmailTemplate;
import com.peter_gerdzhikov.twitter_mail_service.services.implementations.RenderedEmail;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FollowEmailRenderer {

    private static final String FEED_PATH = "/feed";

    private static final String FEED_URL_TOKEN = "FEED_URL";

    private static final String FOLLOWER_USERNAME_TOKEN = "FOLLOWER_USERNAME";

    private static final String FOLLOWEE_USERNAME_TOKEN = "FOLLOWEE_USERNAME";

    private static final String TEXT_TEMPLATE_PATH = "classpath:templates/followEmail.txt";

    private static final String HTML_TEMPLATE_PATH = "classpath:templates/followEmail.html";

    private final String feedUrl;

    private final EmailTemplate emailTemplate;

    public FollowEmailRenderer(@Value("${app.mail.app-base-url}") String appBaseUrl) {
        this.feedUrl = appBaseUrl + FEED_PATH;
        this.emailTemplate = new EmailTemplate(
                HTML_TEMPLATE_PATH,
                TEXT_TEMPLATE_PATH,
                Set.of(FEED_URL_TOKEN, FOLLOWER_USERNAME_TOKEN, FOLLOWEE_USERNAME_TOKEN));
    }

    public RenderedEmail render(String followerUsername, String followeeUsername) {
        return emailTemplate.render(Map.of(
                FEED_URL_TOKEN, feedUrl,
                FOLLOWER_USERNAME_TOKEN, followerUsername,
                FOLLOWEE_USERNAME_TOKEN, followeeUsername));
    }
}
