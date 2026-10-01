package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.SelfFollowException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FollowService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.OutboxService;

@Service
public class FollowServiceImpl implements FollowService {

    private static final long ADDED = 1;

    private static final long REMOVED = -1;

    private final Clock clock;

    private final String topic;

    private final OutboxService outboxService;

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    public FollowServiceImpl(
            Clock clock,
            @Value("${app.kafka.user-followed.name}") String topic,
            OutboxService outboxService,
            UserRepository userRepository,
            FollowRepository followRepository
    ) {
        this.clock = clock;
        this.topic = topic;
        this.outboxService = outboxService;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
    }

    @Override
    @Transactional
    public void follow(UUID followerId, String targetUsername) {
        User target = resolveTarget(followerId, targetUsername);
        Instant now = clock.instant();

        int inserted = followRepository.insertIfAbsent(
                UUID.randomUUID(), followerId, target.getId(), now.truncatedTo(ChronoUnit.MICROS));
        if (inserted == 1) {
            adjustCounts(followerId, target.getId(), ADDED);
            outboxService.enqueue(topic, target.getId().toString(), newFollowedEvent(followerId, target, now));
        }
    }

    @Override
    @Transactional
    public void unfollow(UUID followerId, String targetUsername) {
        UUID targetId = resolveTarget(followerId, targetUsername).getId();

        int deleted = followRepository.deleteByPair(followerId, targetId);
        if (deleted == 1) {
            adjustCounts(followerId, targetId, REMOVED);
        }
    }

    private User resolveTarget(UUID followerId, String targetUsername) {
        User target = userRepository
                .findByUsernameNormalized(targetUsername.toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(UserNotFoundException::new);
        if (target.getId().equals(followerId)) {
            throw new SelfFollowException();
        }

        return target;
    }

    private UserFollowedEventDTO newFollowedEvent(UUID followerId, User target, Instant occurredAt) {
        User follower = userRepository
                .findById(followerId)
                .orElseThrow(UserNotFoundException::new);

        return UserFollowedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .followerId(follower.getId())
                .followeeId(target.getId())
                .occurredAt(occurredAt)
                .followeeEmail(target.getEmail())
                .followerUsername(follower.getUsername())
                .followeeUsername(target.getUsername())
                .build();
    }

    /**
     * The two rows are always updated smaller id first, whichever of them is the follower, so two opposite
     * follows running at once can't each hold one row and wait for the other.
     */
    private void adjustCounts(UUID followerId, UUID targetId, long delta) {
        if (followerId.compareTo(targetId) < 0) {
            userRepository.addToFollowingCount(followerId, delta);
            userRepository.addToFollowersCount(targetId, delta);

        } else {
            userRepository.addToFollowersCount(targetId, delta);
            userRepository.addToFollowingCount(followerId, delta);
        }
    }
}
