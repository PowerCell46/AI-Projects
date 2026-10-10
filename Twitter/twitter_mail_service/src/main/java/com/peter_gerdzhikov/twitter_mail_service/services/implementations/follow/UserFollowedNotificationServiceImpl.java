package com.peter_gerdzhikov.twitter_mail_service.services.implementations.follow;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.implementations.RenderedEmail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.MailDispatchService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.MailEventValidationService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.OutgoingMail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.follow.UserFollowedNotificationService;
import com.peter_gerdzhikov.twitter_mail_service.utilities.Durations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserFollowedNotificationServiceImpl implements UserFollowedNotificationService {

    private static final String KEY_PREFIX = "mail:followed:";

    private static final String SUBJECT_SUFFIX = " followed you";

    private final Clock clock;

    private final Duration window;

    private final FollowEmailRenderer followEmailRenderer;

    private final MailDispatchService mailDispatchService;

    private final MailEventValidationService mailEventValidationService;

    public UserFollowedNotificationServiceImpl(
            Clock clock,
            @Value("${app.mail-inbox.follow-window}") Duration window,
            MailEventValidationService mailEventValidationService,
            MailDispatchService mailDispatchService,
            FollowEmailRenderer followEmailRenderer
    ) {
        this.clock = clock;
        this.window = Durations.requirePositive(window, "app.mail-inbox.follow-window");
        this.mailEventValidationService = mailEventValidationService;
        this.mailDispatchService = mailDispatchService;
        this.followEmailRenderer = followEmailRenderer;
    }

    @Override
    public void process(UserFollowedEventDTO event) {
        mailEventValidationService.validate(event, "follow event with " + ids(event));

        // The pair's sent marker lives for one window, so once the follow is older than that the marker may be gone
        // and a replay (a new consumer group, expired offsets, a dead-letter retry) would mail it a second time.
        if (isOlderThanWindow(event)) {
            log.info("Skipping the follow email for {}: the follow is older than the {} window.", ids(event), window);

            return;
        }

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

    private boolean isOlderThanWindow(UserFollowedEventDTO event) {
        return event.getOccurredAt().isBefore(clock.instant().minus(window));
    }

    private String ids(UserFollowedEventDTO event) {
        return "eventId '" + event.getEventId() + "', followerId '" + event.getFollowerId()
                + "', followeeId '" + event.getFolloweeId() + "'";
    }
}
