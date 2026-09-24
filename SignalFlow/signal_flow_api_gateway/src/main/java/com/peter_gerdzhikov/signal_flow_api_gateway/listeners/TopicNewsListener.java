package com.peter_gerdzhikov.signal_flow_api_gateway.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TopicNewsNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TopicNewsListener {

    private final TopicNewsNotificationService topicNewsNotificationService;

    @KafkaListener(topics = "${app.kafka.topic-news.name}", containerFactory = "topicNewsListenerContainerFactory")
    public void onTopicNews(TopicNewsEventDTO event) {
        topicNewsNotificationService.notifySubscribers(event);
    }
}
