package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedEntryCleanupService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserUnfollowedListener {

    private final FeedEntryCleanupService feedEntryCleanupService;

    @KafkaListener(
            topics = "${app.kafka.user-unfollowed.name}",
            groupId = "${spring.kafka.consumer.group-id}-user-unfollowed",
            containerFactory = "userUnfollowedListenerContainerFactory"
    )
    public void onMessage(UserUnfollowedEventDTO event) {
        feedEntryCleanupService.onUserUnfollowed(event);
    }
}
