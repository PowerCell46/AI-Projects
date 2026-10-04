package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

/**
 * Starts from an empty {@code users} table, like the HTTP suite: the list covers every confirmed user and the
 * Postgres container is shared by all suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class UserListConcurrencyIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int USERS = 10;

    private static final int WRITER_THREADS = 8;

    private static final int READER_THREADS = 4;

    private static final int OPERATIONS_PER_WRITER = 25;

    private static final int WALKS_PER_READER = 5;

    private static final int WALK_PAGE_SIZE = 3;

    private static final int WHOLE_LIST_PAGE_SIZE = 100;

    private static final int EXISTING_USERS = 15;

    private static final int INSERTED_USERS = 20;

    private static final int INSERT_WALK_PAGE_SIZE = 2;

    private static final int MAX_THREADS = 32;

    private static final long SEED = 20261004L;

    private static final long TIMEOUT_SECONDS = 120;

    private static final Instant OLDEST_TIME = Instant.parse("2026-03-01T00:00:00Z");

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void startFromAnEmptyUsersTable() {
        jdbcTemplate.update("DELETE FROM users");
    }

    /**
     * The follow storm leaves hundreds of pending outbox rows, and the outbox publisher test only sees the first
     * batch of pending rows, so this test removes the rows its own users produced.
     */
    @AfterEach
    void removeTheOutboxRowsOfTheTestUsers() {
        jdbcTemplate.update("DELETE FROM outbox WHERE message_key IN (SELECT CAST(id AS text) FROM users)");
    }

    @Test
    void should_keep_every_page_valid_and_every_count_equal_to_its_row_count_when_8_threads_follow_and_unfollow_while_4_threads_page_the_list()
            throws Exception {
        List<User> users = confirmedUsers(USERS);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int writer = 0; writer < WRITER_THREADS; writer++) {
            calls.add(followAndUnfollowAtRandom(users, writer));
        }

        for (int reader = 0; reader < READER_THREADS; reader++) {
            calls.add(pageTheListRepeatedly(users.get(reader)));
        }

        runInParallel(calls);

        assertEveryListedCountEqualsItsRowCount(users.getFirst());
    }

    @Test
    void should_see_every_user_that_existed_at_the_start_exactly_once_when_20_confirmed_users_are_inserted_during_a_walk()
            throws Exception {
        User caller = confirmedUser();
        List<UUID> existingIds = existingUsersOneSecondApart();
        List<Callable<Integer>> calls = new ArrayList<>();
        List<UUID> seen = new ArrayList<>();
        calls.add(() -> {
            seen.addAll(walk(caller, INSERT_WALK_PAGE_SIZE));

            return 0;
        });

        for (int inserted = 0; inserted < INSERTED_USERS; inserted++) {
            calls.add(() -> {
                confirmedUser();

                return 0;
            });
        }

        runInParallel(calls);

        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsAll(existingIds);
    }

    private Callable<Integer> followAndUnfollowAtRandom(List<User> users, int writerIndex) {
        return () -> {
            User actor = users.get(writerIndex);
            Random random = new Random(SEED + writerIndex);
            for (int operation = 0; operation < OPERATIONS_PER_WRITER; operation++) {
                User target = users.get((writerIndex + 1 + random.nextInt(USERS - 1)) % USERS);
                HttpMethod method = random.nextBoolean() ? HttpMethod.PUT : HttpMethod.DELETE;

                assertThat(statusOf(method, target, actor)).isEqualTo(204);
            }

            return 0;
        };
    }

    private Callable<Integer> pageTheListRepeatedly(User caller) {
        return () -> {
            for (int walkNumber = 0; walkNumber < WALKS_PER_READER; walkNumber++) {
                List<UUID> seen = walk(caller, WALK_PAGE_SIZE);

                assertThat(seen).doesNotHaveDuplicates();
                assertThat(seen).hasSize(USERS - 1);
            }

            return 0;
        };
    }

    /**
     * Pages to the end and returns the ids in the order seen. Every page must be a 200 with no negative count.
     */
    private List<UUID> walk(User caller, int size) {
        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            UserListResponseDTO page = pageOf(caller, size, cursor);

            assertThat(page.getItems()).allMatch(item -> item.getFollowersCount() >= 0);
            page.getItems().forEach(item -> seen.add(item.getId()));
            cursor = page.getNextCursor();
        } while (cursor != null);

        return seen;
    }

    private UserListResponseDTO pageOf(User caller, int size, String cursor) {
        String query = "?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);

        return restTestClient
                .get()
                .uri("/api/v1/users" + query)
                .cookie(CookieFactory.COOKIE_NAME, tokenService.mint(caller))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserListResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertEveryListedCountEqualsItsRowCount(User caller) {
        List<UserListItemResponseDTO> listed = pageOf(caller, WHOLE_LIST_PAGE_SIZE, null).getItems();

        assertThat(listed).hasSize(USERS - 1);
        for (UserListItemResponseDTO item : listed) {
            assertThat(item.getFollowersCount())
                    .as("followers of %s", item.getUsername())
                    .isEqualTo(rowsFollowing(item.getId()));
        }
    }

    /**
     * Releases every call at once through a latch, so the requests overlap instead of running in submission
     * order. A failed assertion inside a call fails the test when its result is read.
     */
    private void runInParallel(List<Callable<Integer>> calls) throws Exception {
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

            for (Future<Integer> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

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

    private List<UUID> existingUsersOneSecondApart() {
        List<UUID> orderedIds = new ArrayList<>();
        for (int index = 0; index < EXISTING_USERS; index++) {
            User user = confirmedUser();
            jdbcTemplate.update(
                    "UPDATE users SET created_at = ? WHERE id = ?",
                    Timestamp.from(OLDEST_TIME.plusSeconds(index)),
                    user.getId()
            );
            orderedIds.add(user.getId());
        }

        return orderedIds;
    }

    private List<User> confirmedUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(confirmedUser());
        }

        return users;
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private int rowsFollowing(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM follows WHERE following_id = ?",
                Integer.class,
                userId
        );
    }
}
