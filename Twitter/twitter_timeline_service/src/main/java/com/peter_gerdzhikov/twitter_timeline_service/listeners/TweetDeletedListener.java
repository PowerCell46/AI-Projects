package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedEntryCleanupService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TweetDeletedListener {

    private final FeedEntryCleanupService feedEntryCleanupService;

    @KafkaListener(
            topics = "${app.kafka.tweet-deleted.name}",
            containerFactory = "tweetDeletedListenerContainerFactory"
    )
    public void onMessage(TweetDeletedEventDTO event) {
        feedEntryCleanupService.onTweetDeleted(event);
    }
}
