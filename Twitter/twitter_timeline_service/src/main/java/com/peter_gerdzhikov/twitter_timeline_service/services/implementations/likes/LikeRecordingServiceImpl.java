package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.likes;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes.LikeRecordingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LikeRecordingServiceImpl implements LikeRecordingService {

    private final TweetLikeRepository tweetLikeRepository;

    private final TweetLikeCountRepository tweetLikeCountRepository;

    @Override
    @Transactional
    public boolean like(UUID userId, UUID tweetId, UUID authorId, Instant likedAt) {
        boolean added = tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, likedAt) > 0;
        if (added) {
            tweetLikeCountRepository.increment(tweetId);
        }

        return added;
    }

    @Override
    @Transactional
    public boolean unlike(UUID userId, UUID tweetId) {
        boolean removed = tweetLikeRepository.deleteByUserAndTweet(userId, tweetId) > 0;
        if (removed) {
            tweetLikeCountRepository.decrement(tweetId);
        }

        return removed;
    }
}
