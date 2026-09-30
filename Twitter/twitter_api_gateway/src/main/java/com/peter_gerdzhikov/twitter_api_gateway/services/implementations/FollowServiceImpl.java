package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.SelfFollowException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FollowService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private static final long ADDED = 1;

    private static final long REMOVED = -1;

    private final Clock clock;

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    @Override
    @Transactional
    public void follow(UUID followerId, String targetUsername) {
        UUID targetId = resolveTargetId(followerId, targetUsername);
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        int inserted = followRepository.insertIfAbsent(UUID.randomUUID(), followerId, targetId, createdAt);
        if (inserted == 1) {
            adjustCounts(followerId, targetId, ADDED);
        }
    }

    @Override
    @Transactional
    public void unfollow(UUID followerId, String targetUsername) {
        UUID targetId = resolveTargetId(followerId, targetUsername);

        int deleted = followRepository.deleteByPair(followerId, targetId);
        if (deleted == 1) {
            adjustCounts(followerId, targetId, REMOVED);
        }
    }

    private UUID resolveTargetId(UUID followerId, String targetUsername) {
        UUID targetId = userRepository
                .findByUsernameNormalized(targetUsername.toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .map(User::getId)
                .orElseThrow(UserNotFoundException::new);
        if (targetId.equals(followerId)) {
            throw new SelfFollowException();
        }

        return targetId;
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
