package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.DuplicateSubscriptionException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.SubscriptionLimitExceededException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.SubscriptionNotFoundException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final int maxSubscriptionsPerUser;

    private final UserRepository userRepository;

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionServiceImpl(
            @Value("${app.subscriptions.max-per-user}") int maxSubscriptionsPerUser,
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository
    ) {
        this.maxSubscriptionsPerUser = maxSubscriptionsPerUser;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public List<Subscription> findAllForUser(UUID userId) {
        return subscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public Subscription subscribe(UUID userId, UUID interestTopicId) {
        lockCallersSubscriptions(userId);

        if (subscriptionRepository.existsByUser_IdAndInterestTopicId(userId, interestTopicId)) {
            throw new DuplicateSubscriptionException();
        }

        if (subscriptionRepository.countByUser_Id(userId) >= maxSubscriptionsPerUser) {
            throw new SubscriptionLimitExceededException();
        }

        try {
            // Flushed inside the transaction so a constraint violation surfaces here, not at commit.
            Subscription savedSubscription = subscriptionRepository
                    .saveAndFlush(newSubscription(userId, interestTopicId));
            log.debug("User '{}' subscribed to interest topic '{}'.", userId, interestTopicId);
            return savedSubscription;

        } catch (DataIntegrityViolationException e) {
            if (!isUniqueConstraintViolation(e)) {
                // Not the (interest_topic_id, user_id) constraint - most likely the user_id FK, meaning
                // the caller's own user row is gone. Rethrown rather than mislabeled, so the generic
                // handler's neutral 409 answers instead of a wrong "already subscribed".
                throw e;
            }

            // The unique (interest_topic_id, user_id) constraint - a concurrent request won the race.
            throw new DuplicateSubscriptionException();
        }
    }

    @Override
    public void unsubscribe(UUID userId, UUID interestTopicId) {
        Subscription subscription = subscriptionRepository
                .findByUser_IdAndInterestTopicId(userId, interestTopicId)
                .orElseThrow(SubscriptionNotFoundException::new);

        subscriptionRepository.delete(subscription);
        log.debug("User '{}' unsubscribed from interest topic '{}'.", userId, interestTopicId);
    }

    /**
     * Holds a row lock on the user for the rest of the transaction, serialising that one user's
     * concurrent subscribes. Without it the cap is check-then-act: concurrent requests for distinct
     * topics all read the same stale count and all pass it, overrunning the cap.
     */
    private void lockCallersSubscriptions(UUID userId) {
        userRepository.lockById(userId);
    }

    /**
     * Attaches the user as a lazy proxy rather than loading it, so an authenticated request never
     * reads the database for its own identity.
     */
    private Subscription newSubscription(UUID userId, UUID interestTopicId) {
        Subscription subscription = new Subscription();
        subscription.setUser(userRepository.getReferenceById(userId));
        subscription.setInterestTopicId(interestTopicId);
        return subscription;
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState());
    }
}
