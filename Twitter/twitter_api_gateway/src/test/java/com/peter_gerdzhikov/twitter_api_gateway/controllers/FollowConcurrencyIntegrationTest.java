package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class FollowConcurrencyIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int FOLLOWERS = 50;

    private static final int SAME_PAIR_CALLS = 20;

    private static final int MUTUAL_ROUNDS = 50;

    private static final int STORM_USERS = 8;

    private static final int STORM_OPERATIONS = 200;

    private static final int MAX_THREADS = 32;

    private static final long SEED = 20260930L;

    private static final long TIMEOUT_SECONDS = 120;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Test
    void should_store_exactly_50_rows_and_count_50_when_50_users_follow_one_target_in_parallel() throws Exception {
        User target = confirmedUser();
        List<User> followers = confirmedUsers(FOLLOWERS);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (User follower : followers) {
            calls.add(() -> statusOf(HttpMethod.PUT, target, follower));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(204);
        assertThat(rowsFollowing(target)).isEqualTo(FOLLOWERS);
        assertThat(reload(target).getFollowersCount()).isEqualTo(FOLLOWERS);
        assertConsistent(followers);
        assertConsistent(List.of(target));
    }

    @Test
    void should_store_one_row_and_count_one_when_the_same_pair_follows_in_parallel() throws Exception {
        User follower = confirmedUser();
        User target = confirmedUser();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < SAME_PAIR_CALLS; i++) {
            calls.add(() -> statusOf(HttpMethod.PUT, target, follower));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(204);
        assertThat(rowsFollowing(target)).isEqualTo(1);
        assertThat(reload(target).getFollowersCount()).isEqualTo(1);
        assertThat(reload(follower).getFollowingCount()).isEqualTo(1);
    }

    @Test
    void should_count_both_sides_without_deadlock_when_two_users_follow_each_other_in_parallel_50_times()
            throws Exception {
        for (int round = 0; round < MUTUAL_ROUNDS; round++) {
            User first = confirmedUser();
            User second = confirmedUser();

            List<Integer> statuses = runInParallel(List.of(
                    () -> statusOf(HttpMethod.PUT, second, first),
                    () -> statusOf(HttpMethod.PUT, first, second)
            ));

            assertThat(statuses).as("round %d", round).containsOnly(204);
            assertThat(reload(first).getFollowersCount()).isEqualTo(1);
            assertThat(reload(first).getFollowingCount()).isEqualTo(1);
            assertThat(reload(second).getFollowersCount()).isEqualTo(1);
            assertThat(reload(second).getFollowingCount()).isEqualTo(1);
        }
    }

    @Test
    void should_keep_every_counter_equal_to_its_row_count_when_follows_and_unfollows_storm_in_parallel()
            throws Exception {
        List<User> users = confirmedUsers(STORM_USERS);
        Random random = new Random(SEED);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < STORM_OPERATIONS; i++) {
            User actor = users.get(random.nextInt(STORM_USERS));
            User target = users.get((users.indexOf(actor) + 1 + random.nextInt(STORM_USERS - 1)) % STORM_USERS);
            HttpMethod method = random.nextBoolean() ? HttpMethod.PUT : HttpMethod.DELETE;
            calls.add(() -> statusOf(method, target, actor));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(204);
        assertConsistent(users);
    }

    private void assertConsistent(List<User> users) {
        for (User user : users) {
            User found = reload(user);

            assertThat(found.getFollowersCount()).as("followers of %s", user.getUsername()).isEqualTo(rowsFollowing(user));
            assertThat(found.getFollowingCount()).as("following of %s", user.getUsername()).isEqualTo(rowsBy(user));
        }
    }

    /**
     * Releases every call at once through a latch, so the requests overlap instead of running in submission
     * order.
     */
    private List<Integer> runInParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(calls.size(), MAX_THREADS));
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }

            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return statuses;

        } finally {
            executor.shutdownNow();
        }
    }

    private int statusOf(HttpMethod method, User target, User actor) {
        return restTestClient
                .method(method)
                .uri("/api/v1/users/{username}/follow", target.getUsername())
                .cookie(CookieFactory.COOKIE_NAME, tokenService.mint(actor))
                .exchange()
                .returnResult()
                .getStatus()
                .value();
    }

    private List<User> confirmedUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(confirmedUser());
        }

        return users;
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private int rowsFollowing(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM follows WHERE following_id = ?",
                Integer.class,
                user.getId()
        );
    }

    private int rowsBy(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM follows WHERE follower_id = ?",
                Integer.class,
                user.getId()
        );
    }
}
