package com.peter_gerdzhikov.twitter_mail_service.services.implementations.follow;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.implementations.RenderedEmail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.MailDispatchService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.MailEventValidationService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.OutgoingMail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.follow.UserFollowedNotificationService;

@Service
public class UserFollowedNotificationServiceImpl implements UserFollowedNotificationService {

    private static final String KEY_PREFIX = "mail:followed:";

    private static final String SUBJECT_SUFFIX = " followed you";

    private final Duration window;

    private final FollowEmailRenderer followEmailRenderer;

    private final MailDispatchService mailDispatchService;

    private final MailEventValidationService mailEventValidationService;

    public UserFollowedNotificationServiceImpl(
            @Value("${app.mail-inbox.follow-window}") Duration window,
            MailEventValidationService mailEventValidationService,
            MailDispatchService mailDispatchService,
            FollowEmailRenderer followEmailRenderer
    ) {
        this.window = requirePositive(window);
        this.mailEventValidationService = mailEventValidationService;
        this.mailDispatchService = mailDispatchService;
        this.followEmailRenderer = followEmailRenderer;
    }

    @Override
    public void process(UserFollowedEventDTO event) {
        mailEventValidationService.validate(event, "follow event with " + ids(event));

        RenderedEmail renderedEmail = followEmailRenderer.render(
                event.getFollowerUsername(), event.getFolloweeUsername());

        mailDispatchService.dispatchOnce(OutgoingMail.builder()
                .key(KEY_PREFIX + event.getFollowerId() + ":" + event.getFolloweeId())
                .html(renderedEmail.getHtml())
                .text(renderedEmail.getText())
                .subject(event.getFollowerUsername() + SUBJECT_SUFFIX)
                .logLabel("follow email for " + ids(event))
                .recipient(event.getFolloweeEmail())
                .sentTtl(window)
                .build());
    }

    private Duration requirePositive(Duration window) {
        if (window.isZero() || window.isNegative()) {
            throw new IllegalStateException("app.mail-inbox.follow-window must be positive, but was " + window + ".");
        }

        return window;
    }

    private String ids(UserFollowedEventDTO event) {
        return "eventId '" + event.getEventId() + "', followerId '" + event.getFollowerId()
                + "', followeeId '" + event.getFolloweeId() + "'";
    }
}
