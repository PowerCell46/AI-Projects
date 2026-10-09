package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.views;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.views.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewRecordingService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.views.TweetIdsValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ViewServiceImpl implements ViewService {

    private static final int MAX_REPORTED_TWEETS = 50;

    private static final int MAX_READ_TWEETS = 100;

    private final TweetLookupService tweetLookupService;

    private final ViewRecordingService viewRecordingService;

    private final TweetViewCountRepository tweetViewCountRepository;

    @Override
    public void report(UUID viewerId, List<UUID> tweetIds) {
        TweetIdsValidator.validate(tweetIds, MAX_REPORTED_TWEETS);

        // The tweet call comes before the transaction, so no connection is held while waiting for it.
        Set<UUID> existingTweetIds = tweetLookupService
                .findByIds(new LinkedHashSet<>(tweetIds))
                .keySet();
        if (existingTweetIds.isEmpty()) {
            return;
        }

        viewRecordingService.record(viewerId, existingTweetIds);
    }

    @Override
    public Map<UUID, Long> getViews(List<UUID> tweetIds) {
        TweetIdsValidator.validate(tweetIds, MAX_READ_TWEETS);

        return countViews(tweetIds);
    }

    @Override
    public Map<UUID, Long> countViews(Collection<UUID> tweetIds) {
        Set<UUID> distinctTweetIds = new LinkedHashSet<>(tweetIds);
        Map<UUID, Long> viewsByTweetId = new LinkedHashMap<>();
        distinctTweetIds.forEach(tweetId -> viewsByTweetId.put(tweetId, 0L));
        tweetViewCountRepository
                .findAllById(distinctTweetIds)
                .forEach(count -> viewsByTweetId.put(count.getTweetId(), count.getViews()));

        return viewsByTweetId;
    }
}
