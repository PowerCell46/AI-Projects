package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedBackfillService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFollowedListener {

    private final FeedBackfillService feedBackfillService;

    @KafkaListener(
            topics = "${app.kafka.user-followed.name}",
            groupId = "${spring.kafka.consumer.group-id}-user-followed",
            containerFactory = "userFollowedListenerContainerFactory"
    )
    public void onMessage(UserFollowedEventDTO event) {
        feedBackfillService.backfill(event);
    }
}
