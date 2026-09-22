package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.DuplicateSubscriptionException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.SubscriptionLimitExceededException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionService;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

/**
 * The cap is read-then-write, so only real concurrency proves it holds - a mocked repository cannot
 * produce the stale count that made concurrent subscribes overrun it.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionServiceImplConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "user@example.com";

    private static final String PASSWORD = "hashed-password";

    private static final int CONCURRENT_SUBSCRIBES = 8;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Value("${app.subscriptions.max-per-user}")
    private int maxSubscriptionsPerUser;

    @BeforeEach
    void clearSubscriptionsAndUsers() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void should_not_exceed_the_limit_when_subscribes_to_distinct_topics_arrive_concurrently() throws InterruptedException {
        User user = userRepository.save(newUser());

        int accepted = subscribeConcurrently(user.getId(), CONCURRENT_SUBSCRIBES);

        assertThat(accepted).isEqualTo(maxSubscriptionsPerUser);
        assertThat(subscriptionRepository.countByUser_Id(user.getId())).isEqualTo(maxSubscriptionsPerUser);
    }

    @Test
    void should_accept_only_one_of_two_concurrent_subscribes_to_the_same_topic() throws InterruptedException {
        User user = userRepository.save(newUser());
        UUID interestTopicId = UUID.randomUUID();

        int accepted = runConcurrently(2, () -> subscriptionService.subscribe(user.getId(), interestTopicId));

        assertThat(accepted).isEqualTo(1);
        assertThat(subscriptionRepository.countByUser_Id(user.getId())).isEqualTo(1);
    }

    private int subscribeConcurrently(UUID userId, int requests) throws InterruptedException {
        return runConcurrently(requests, () -> subscriptionService.subscribe(userId, UUID.randomUUID()));
    }

    /**
     * Releases every task at once and returns how many completed without a rejection, so a caller can
     * assert on what the service let through rather than on thread bookkeeping.
     */
    private int runConcurrently(int tasks, Runnable task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(tasks);
        AtomicInteger accepted = new AtomicInteger();
        List<Exception> unexpectedFailures = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(tasks);
        try {
            for (int i = 0; i < tasks; i++) {
                executor.execute(() -> {
                    try {
                        startGate.await();
                        task.run();
                        accepted.incrementAndGet();

                    } catch (DuplicateSubscriptionException | SubscriptionLimitExceededException e) {
                        // Expected - the request that lost the race.

                    } catch (Exception e) {
                        unexpectedFailures.add(e);

                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();

        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedFailures).isEmpty();
        return accepted.get();
    }

    private User newUser() {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPassword(PASSWORD);
        user.setRole(Role.USER);
        return user;
    }
}
