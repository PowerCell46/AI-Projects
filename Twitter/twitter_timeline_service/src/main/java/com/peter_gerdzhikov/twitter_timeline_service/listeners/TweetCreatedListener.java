package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedFanOutService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TweetCreatedListener {

    private final FeedFanOutService feedFanOutService;

    @KafkaListener(
            topics = "${app.kafka.tweet-created.name}",
            containerFactory = "tweetCreatedListenerContainerFactory"
    )
    public void onMessage(TweetCreatedEventDTO event) {
        feedFanOutService.fanOut(event);
    }
}
