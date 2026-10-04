package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.ViewRecordingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ViewRecordingServiceImpl implements ViewRecordingService {

    private final TweetViewRepository tweetViewRepository;

    private final TweetViewCountRepository tweetViewCountRepository;

    @Override
    @Transactional
    public int record(UUID viewerId, Collection<UUID> tweetIds) {
        List<UUID> newTweetIds = tweetViewRepository.insertIfAbsent(viewerId, tweetIds.toArray(UUID[]::new));
        if (!newTweetIds.isEmpty()) {
            tweetViewCountRepository.incrementAll(newTweetIds.toArray(UUID[]::new));
        }

        return newTweetIds.size();
    }
}
