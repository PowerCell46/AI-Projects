package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.SqlStatementCounter;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.follows.FollowCursorCodec;

import jakarta.persistence.EntityManagerFactory;

/**
 * Every test starts from an empty {@code users} table, because the list covers every confirmed user and the
 * Postgres container is shared by all suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UserListControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-01T00:00:00Z");

    private static final Set<String> ITEM_KEYS = Set.of(
            "id",
            "bio",
            "username",
            "followersCount",
            "followedByMe",
            "profilePictureUrl"
    );

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void startFromAnEmptyUsersTable() {
        jdbcTemplate.update("DELETE FROM users");
    }

    private User confirmedUser() {
        return saveUser(true);
    }

    private User saveUser(boolean enabled) {
        User user = TestEntities.newUser();
        user.setEnabled(enabled);

        return userRepository.save(user);
    }

    private User userCreatedAt(Instant createdAt) {
        User user = confirmedUser();
        setCreatedAt(user, createdAt);

        return user;
    }

    private void setCreatedAt(User user, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                user.getId()
        );
    }

    /**
     * Creates {@code count} confirmed users whose registration times run one second apart, oldest first.
     */
    private List<User> usersOneSecondApart(int count) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(userCreatedAt(BASE_TIME.plusSeconds(index)));
        }

        return users;
    }

    private List<User> usersSharingOneTimestamp(int count) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(userCreatedAt(BASE_TIME));
        }

        return users;
    }

    private List<UUID> newestFirst(List<User> usersOldestFirst) {
        return usersOldestFirst
                .reversed()
                .stream()
                .map(User::getId)
                .toList();
    }

    private List<UUID> byIdDescending(List<User> users) {
        return users
                .stream()
                .map(User::getId)
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();
    }

    private List<UUID> idsOf(UserListResponseDTO page) {
        return page
                .getItems()
                .stream()
                .map(UserListItemResponseDTO::getId)
                .toList();
    }

    private String cookieOf(User user) {
        return tokenService.mint(user);
    }

    private RestTestClient.ResponseSpec get(String query, String cookie) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient
                .get()
                .uri("/api/v1/users" + query);
        if (cookie != null) {
            request.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        return request.exchange();
    }

    private RestTestClient.ResponseSpec get(String query, User caller) {
        return get(query, cookieOf(caller));
    }

    private UserListResponseDTO pageOf(String query, User caller) {
        return get(query, caller)
                .expectStatus()
                .isOk()
                .expectBody(UserListResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawPageOf(String query, User caller) {
        return get(query, caller)
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private String bodyTextOf(String query, User caller) {
        return get(query, caller)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertError(RestTestClient.ResponseSpec response, int status, String message) {
        ErrorResponseDTO body = response
                .expectStatus()
                .isEqualTo(status)
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getMessages()).containsExactly(message);
    }

    private String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String cursorOf(Instant createdAt, UUID id) {
        return FollowCursorCodec.encode(createdAt, id);
    }

    /**
     * Pages to the end and returns the ids in the order seen. {@code betweenPages} runs after every page that
     * has a next page, before that next page is requested.
     */
    private List<UUID> walk(User caller, int size, Runnable betweenPages) {
        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            String query = "?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
            UserListResponseDTO page = pageOf(query, caller);
            seen.addAll(idsOf(page));
            cursor = page.getNextCursor();
            if (cursor != null) {
                betweenPages.run();
            }
        } while (cursor != null);

        return seen;
    }

    private void follow(User follower, User target) {
        send(HttpMethod.PUT, follower, target)
                .expectStatus()
                .isNoContent();
    }

    private void unfollow(User follower, User target) {
        send(HttpMethod.DELETE, follower, target)
                .expectStatus()
                .isNoContent();
    }

    private RestTestClient.ResponseSpec send(HttpMethod method, User follower, User target) {
        return restTestClient
                .method(method)
                .uri("/api/v1/users/{username}/follow", target.getUsername())
                .cookie(CookieFactory.COOKIE_NAME, cookieOf(follower))
                .exchange();
    }

    private UserListItemResponseDTO itemFor(UserListResponseDTO page, User user) {
        return page
                .getItems()
                .stream()
                .filter(item -> item.getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private void giveProfilePicture(User user) {
        DbFile picture = dbFileRepository.save(TestEntities.newDbFile());
        user.setProfilePicture(picture);
        userRepository.save(user);
    }

    @Nested
    class Content {

        @Test
        void should_return_exactly_items_and_next_cursor_when_the_list_is_requested() {
            User caller = confirmedUser();
            confirmedUser();

            Map<String, Object> body = rawPageOf("", caller);

            assertThat(body.keySet()).containsExactlyInAnyOrder("items", "nextCursor");
        }

        @Test
        @SuppressWarnings("unchecked")
        void should_return_exactly_the_six_item_keys_when_a_user_is_listed() {
            User caller = confirmedUser();
            confirmedUser();

            List<Map<String, Object>> items = (List<Map<String, Object>>) rawPageOf("", caller).get("items");
            Map<String, Object> item = items.getFirst();

            assertThat(item.keySet()).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
        }

        @Test
        void should_carry_no_private_or_extra_key_anywhere_when_users_are_listed() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            listed.setBio("Bio");
            listed.setLocation("Sofia");
            listed.setBirthdate(LocalDate.of(1990, 5, 17));
            listed.setProfileCoverPicture(dbFileRepository.save(TestEntities.newDbFile()));
            userRepository.save(listed);

            String body = bodyTextOf("", caller);

            assertThat(body)
                    .doesNotContain("email", "password", "location", "birthdate", "createdAt", "updatedAt")
                    .doesNotContain("followingCount", "coverPictureUrl", listed.getEmail(), listed.getPassword());
        }

        @Test
        void should_keep_the_registered_case_of_the_username_when_a_user_is_listed() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            listed.setUsername("MixedCase_1");
            userRepository.save(listed);

            UserListResponseDTO page = pageOf("", caller);

            assertThat(page.getItems())
                    .extracting(UserListItemResponseDTO::getUsername)
                    .containsExactly("MixedCase_1");
        }

        @Test
        void should_return_a_null_picture_url_when_the_user_has_no_picture() {
            User caller = confirmedUser();
            User listed = confirmedUser();

            assertThat(itemFor(pageOf("", caller), listed).getProfilePictureUrl()).isNull();
        }

        @Test
        void should_return_the_file_url_when_the_user_has_a_picture() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            giveProfilePicture(listed);

            String url = itemFor(pageOf("", caller), listed).getProfilePictureUrl();

            assertThat(url).isEqualTo("/api/v1/files/" + listed.getProfilePicture().getId());
        }

        @Test
        void should_return_a_null_bio_when_the_user_has_no_bio() {
            User caller = confirmedUser();
            User listed = confirmedUser();

            assertThat(itemFor(pageOf("", caller), listed).getBio()).isNull();
        }

        @Test
        void should_return_bio_and_username_verbatim_as_json_text_when_they_hold_emoji_script_tags_and_quotes() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            String username = "😀<b>\"'x";
            String bio = "😀 <script>alert('x')</script> \"quoted\"";
            listed.setUsername(username);
            listed.setBio(bio);
            userRepository.save(listed);

            get("", caller)
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
            UserListItemResponseDTO item = itemFor(pageOf("", caller), listed);

            assertThat(item.getUsername()).isEqualTo(username);
            assertThat(item.getBio()).isEqualTo(bio);
        }

        @Test
        void should_return_the_newest_account_first_when_the_users_have_different_creation_times() {
            User caller = userCreatedAt(BASE_TIME.minusSeconds(60));
            List<User> listed = usersOneSecondApart(3);

            UserListResponseDTO page = pageOf("", caller);

            assertThat(idsOf(page)).containsExactlyElementsOf(newestFirst(listed));
        }

        @Test
        void should_leave_the_caller_out_when_the_caller_is_the_newest_account() {
            User older = userCreatedAt(BASE_TIME);
            User caller = userCreatedAt(BASE_TIME.plusSeconds(10));

            UserListResponseDTO page = pageOf("", caller);

            assertThat(idsOf(page)).containsExactly(older.getId());
        }

        @Test
        void should_leave_out_unconfirmed_users_when_users_are_listed() {
            User caller = confirmedUser();
            saveUser(false);
            User confirmed = confirmedUser();

            UserListResponseDTO page = pageOf("", caller);

            assertThat(idsOf(page)).containsExactly(confirmed.getId());
        }

        @Test
        void should_return_empty_items_and_a_null_next_cursor_when_the_caller_is_alone() {
            UserListResponseDTO page = pageOf("", confirmedUser());

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
        }
    }

    @Nested
    class FollowState {

        @Test
        void should_mark_followed_by_me_true_only_for_the_users_the_caller_follows() {
            User caller = confirmedUser();
            User followed = confirmedUser();
            User other = confirmedUser();
            follow(caller, followed);

            UserListResponseDTO page = pageOf("", caller);

            assertThat(itemFor(page, followed).isFollowedByMe()).isTrue();
            assertThat(itemFor(page, other).isFollowedByMe()).isFalse();
        }

        @Test
        void should_mark_followed_by_me_false_when_a_user_follows_the_caller_without_being_followed_back() {
            User caller = confirmedUser();
            User follower = confirmedUser();
            follow(follower, caller);

            assertThat(itemFor(pageOf("", caller), follower).isFollowedByMe()).isFalse();
        }

        @Test
        void should_mark_followed_by_me_true_on_the_next_call_when_the_caller_follows_a_user() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            assertThat(itemFor(pageOf("", caller), listed).isFollowedByMe()).isFalse();

            follow(caller, listed);

            assertThat(itemFor(pageOf("", caller), listed).isFollowedByMe()).isTrue();
        }

        @Test
        void should_mark_followed_by_me_false_on_the_next_call_when_the_caller_unfollows_a_user() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            follow(caller, listed);

            unfollow(caller, listed);

            assertThat(itemFor(pageOf("", caller), listed).isFollowedByMe()).isFalse();
        }

        @Test
        void should_mark_followed_by_me_true_on_both_lists_when_two_users_follow_each_other() {
            User first = confirmedUser();
            User second = confirmedUser();
            follow(first, second);
            follow(second, first);

            assertThat(itemFor(pageOf("", first), second).isFollowedByMe()).isTrue();
            assertThat(itemFor(pageOf("", second), first).isFollowedByMe()).isTrue();
        }

        @Test
        void should_return_the_real_follower_count_when_a_user_has_followers() {
            User caller = confirmedUser();
            User popular = confirmedUser();
            follow(confirmedUser(), popular);
            follow(confirmedUser(), popular);
            follow(confirmedUser(), popular);

            assertThat(itemFor(pageOf("?size=100", caller), popular).getFollowersCount()).isEqualTo(3);
        }

        @Test
        void should_raise_the_followers_count_by_one_when_another_user_follows_them() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            long before = itemFor(pageOf("", caller), listed).getFollowersCount();

            follow(confirmedUser(), listed);

            assertThat(itemFor(pageOf("?size=100", caller), listed).getFollowersCount()).isEqualTo(before + 1);
        }

        @Test
        void should_lower_the_followers_count_by_one_when_another_user_unfollows_them() {
            User caller = confirmedUser();
            User listed = confirmedUser();
            User follower = confirmedUser();
            follow(follower, listed);
            long before = itemFor(pageOf("?size=100", caller), listed).getFollowersCount();

            unfollow(follower, listed);

            assertThat(itemFor(pageOf("?size=100", caller), listed).getFollowersCount()).isEqualTo(before - 1);
        }

        @Test
        void should_keep_the_followers_count_of_the_others_when_the_caller_follows_one_user() {
            User caller = confirmedUser();
            User followed = confirmedUser();
            User other = confirmedUser();
            long otherBefore = itemFor(pageOf("", caller), other).getFollowersCount();

            follow(caller, followed);

            UserListResponseDTO page = pageOf("", caller);
            assertThat(itemFor(page, other).getFollowersCount()).isEqualTo(otherBefore);
            assertThat(itemFor(page, followed).getFollowersCount()).isEqualTo(1);
        }
    }

    @Nested
    class Paging {

        @Test
        void should_return_20_items_and_a_next_cursor_when_no_size_is_given_and_25_people_exist() {
            User caller = confirmedUser();
            usersOneSecondApart(25);

            UserListResponseDTO page = pageOf("", caller);

            assertThat(page.getItems()).hasSize(20);
            assertThat(page.getNextCursor()).isNotNull();
        }

        @Test
        void should_return_the_last_5_items_and_a_null_next_cursor_when_the_cursor_of_the_first_page_is_followed() {
            User caller = confirmedUser();
            usersOneSecondApart(25);
            String cursor = pageOf("", caller).getNextCursor();

            UserListResponseDTO secondPage = pageOf("?cursor=" + cursor, caller);

            assertThat(secondPage.getItems()).hasSize(5);
            assertThat(secondPage.getNextCursor()).isNull();
        }

        @Test
        void should_return_every_other_confirmed_user_exactly_once_when_the_pages_are_followed_to_the_end() {
            User caller = confirmedUser();
            List<User> others = usersOneSecondApart(20);

            List<UUID> seen = walk(caller, 7, () -> { });

            assertThat(seen).containsExactlyElementsOf(newestFirst(others));
        }

        @Test
        void should_return_every_other_confirmed_user_exactly_once_when_all_users_share_the_same_created_at() {
            User caller = userCreatedAt(BASE_TIME.minusSeconds(60));
            List<User> others = usersSharingOneTimestamp(9);

            List<UUID> seen = walk(caller, 4, () -> { });

            assertThat(seen).containsExactlyElementsOf(byIdDescending(others));
        }

        @Test
        void should_skip_a_user_confirmed_mid_walk_with_a_newer_created_at_and_repeat_nobody_when_the_walk_continues() {
            User caller = confirmedUser();
            List<User> others = usersOneSecondApart(10);
            List<User> lateComers = new ArrayList<>();

            List<UUID> seen = walk(caller, 3, () -> {
                if (lateComers.isEmpty()) {
                    lateComers.add(userCreatedAt(BASE_TIME.plusSeconds(100)));
                }
            });

            assertThat(lateComers).hasSize(1);
            assertThat(seen).containsExactlyElementsOf(newestFirst(others));
            assertThat(seen).doesNotContain(lateComers.getFirst().getId());
        }

        @Test
        void should_show_a_user_confirmed_mid_walk_with_an_older_created_at_once_on_a_later_page() {
            User caller = confirmedUser();
            List<User> others = usersOneSecondApart(10);
            List<User> earlyRegistrations = new ArrayList<>();

            List<UUID> seen = walk(caller, 3, () -> {
                if (earlyRegistrations.isEmpty()) {
                    earlyRegistrations.add(userCreatedAt(BASE_TIME.minusSeconds(100)));
                }
            });

            List<UUID> expected = new ArrayList<>(newestFirst(others));
            expected.add(earlyRegistrations.getFirst().getId());
            assertThat(seen).containsExactlyElementsOf(expected);
        }

        @Test
        void should_continue_from_the_right_place_when_the_cursor_row_was_deleted_between_requests() {
            User caller = confirmedUser();
            List<User> others = usersOneSecondApart(8);
            UserListResponseDTO firstPage = pageOf("?size=3", caller);
            User cursorRow = others.get(5);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", cursorRow.getId());

            UserListResponseDTO secondPage = pageOf("?size=3&cursor=" + firstPage.getNextCursor(), caller);

            assertThat(idsOf(secondPage)).containsExactly(others.get(4).getId(), others.get(3).getId(), others.get(2).getId());
        }

        @Test
        void should_return_a_null_next_cursor_when_the_entries_exactly_fill_the_page() {
            User caller = confirmedUser();
            usersOneSecondApart(4);

            UserListResponseDTO page = pageOf("?size=4", caller);

            assertThat(page.getItems()).hasSize(4);
            assertThat(page.getNextCursor()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "100"})
        void should_return_200_when_the_size_is_at_a_boundary(String size) {
            get("?size=" + size, confirmedUser())
                    .expectStatus()
                    .isOk();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "101"})
        void should_return_400_with_the_page_size_message_when_the_size_is_out_of_range(String size) {
            assertError(get("?size=" + size, confirmedUser()), 400, InvalidPageSizeException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.5", "1;DROP TABLE users", "2147483648"})
        void should_return_400_with_the_malformed_parameter_message_when_the_size_is_not_an_int(String size) {
            assertError(get("?size=" + encoded(size), confirmedUser()), 400, "Malformed request parameter.");
        }

        @Test
        void should_use_the_first_value_and_return_200_when_the_size_is_repeated() {
            User caller = confirmedUser();
            usersOneSecondApart(3);

            assertThat(pageOf("?size=1&size=2", caller).getItems()).hasSize(1);
        }

        @Test
        void should_return_20_items_when_the_size_is_empty() {
            User caller = confirmedUser();
            usersOneSecondApart(25);

            assertThat(pageOf("?size=", caller).getItems()).hasSize(20);
        }

        @ParameterizedTest
        @ValueSource(strings = {"garbage", "!!!", "' OR 1=1", "<script>", "../", "\r\n", "\0", " "})
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_garbage_or_hostile_text(String cursor) {
            assertError(get("?cursor=" + encoded(cursor), confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @Test
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_valid_base64url_but_has_the_wrong_content() {
            String cursor = Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

            assertError(get("?cursor=" + cursor, confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"padded", "upper_case_id", "leading_zero_micros", "signed_micros"})
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_not_the_form_the_codec_produces(String form) {
            String id = UUID.randomUUID().toString();
            String raw = switch (form) {
                case "upper_case_id" -> "1000000:" + id.toUpperCase();
                case "leading_zero_micros" -> "01000000:" + id;
                case "signed_micros" -> "+1000000:" + id;
                default -> "1000000:" + id;
            };
            Base64.Encoder encoder = form.equals("padded")
                    ? Base64.getUrlEncoder()
                    : Base64.getUrlEncoder().withoutPadding();
            String cursor = encoder.encodeToString(raw.getBytes(StandardCharsets.UTF_8));

            assertError(get("?cursor=" + encoded(cursor), confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @Test
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_empty() {
            assertError(get("?cursor=", confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @Test
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_repeated() {
            assertError(get("?cursor=a&cursor=b", confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @Test
        void should_return_400_with_the_invalid_cursor_message_when_the_cursor_is_5000_characters_long() {
            assertError(get("?cursor=" + "A".repeat(5000), confirmedUser()), 400, InvalidCursorException.MESSAGE);
        }

        @Test
        void should_return_the_page_size_message_when_both_the_size_and_the_cursor_are_bad() {
            assertError(get("?size=0&cursor=garbage", confirmedUser()), 400, InvalidPageSizeException.MESSAGE);
        }

        @Test
        void should_return_200_with_the_whole_list_when_the_cursor_is_at_the_far_end() {
            User caller = confirmedUser();
            List<User> others = usersOneSecondApart(3);
            Instant farFuture = Instant.EPOCH.plus(999_999_999_999_999_999L, ChronoUnit.MICROS);
            String cursor = cursorOf(farFuture, new UUID(0, 0));

            UserListResponseDTO page = pageOf("?cursor=" + cursor, caller);

            assertThat(idsOf(page)).containsExactlyElementsOf(newestFirst(others));
        }

        @Test
        void should_return_200_with_empty_items_and_a_null_next_cursor_when_the_cursor_is_at_the_start() {
            User caller = confirmedUser();
            usersOneSecondApart(3);
            String cursor = cursorOf(Instant.EPOCH, UUID.randomUUID());

            UserListResponseDTO page = pageOf("?cursor=" + cursor, caller);

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
        }

        @Test
        void should_ignore_the_parameter_and_return_200_when_an_unknown_query_parameter_is_sent() {
            User caller = confirmedUser();
            User listed = confirmedUser();

            UserListResponseDTO page = pageOf("?unknown=1", caller);

            assertThat(idsOf(page)).containsExactly(listed.getId());
        }

        @Test
        void should_return_identical_bodies_when_the_same_call_is_made_twice() {
            User caller = confirmedUser();
            usersOneSecondApart(5);

            assertThat(bodyTextOf("?size=3", caller)).isEqualTo(bodyTextOf("?size=3", caller));
        }
    }

    @Nested
    class Identity {

        @Test
        void should_return_401_when_there_is_no_cookie() {
            get("", (String) null)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_token_is_expired() {
            Instant issuedAt = Instant.now().minusSeconds(7200);
            String token = TestJwts.sign(TestJwts.validClaims()
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(issuedAt.plusSeconds(3600))), jwtSecret);

            get("", token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_token_is_tampered() {
            String token = TestJwts.withTamperedSignature(cookieOf(confirmedUser()));

            get("", token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_token_is_signed_with_another_secret() {
            String token = TestJwts.sign(TestJwts.validClaims(), "another-secret-that-is-long-enough-for-hs256-use");

            get("", token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_list_every_confirmed_user_with_followed_by_me_false_when_the_token_belongs_to_a_deleted_user() {
            List<User> others = usersOneSecondApart(3);
            String token = TestJwts.sign(TestJwts.validClaims(), jwtSecret);

            UserListResponseDTO page = get("", token)
                    .expectStatus()
                    .isOk()
                    .expectBody(UserListResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(idsOf(page)).containsExactlyElementsOf(newestFirst(others));
            assertThat(page.getItems()).allMatch(item -> !item.isFollowedByMe());
        }

        @Test
        void should_leave_out_the_cookie_user_and_ignore_the_header_when_x_user_id_headers_are_forged() {
            User caller = confirmedUser();
            User victim = confirmedUser();

            UserListResponseDTO page = restTestClient
                    .get()
                    .uri("/api/v1/users")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(caller))
                    .header("X-User-Id", victim.getId().toString())
                    .header("X-User-Name", victim.getUsername())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(UserListResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(idsOf(page)).containsExactly(victim.getId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
        void should_return_405_with_an_allow_header_containing_get_when_another_method_is_used(String method) {
            restTestClient
                    .method(HttpMethod.valueOf(method))
                    .uri("/api/v1/users")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(confirmedUser()))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                    .expectHeader()
                    .value("Allow", allow -> assertThat(allow).contains("GET"));
        }

        @Test
        void should_return_200_when_the_method_is_head() {
            restTestClient
                    .head()
                    .uri("/api/v1/users")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(confirmedUser()))
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        @Test
        void should_return_404_when_the_path_has_a_trailing_slash() {
            ErrorResponseDTO body = restTestClient
                    .get()
                    .uri("/api/v1/users/")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(confirmedUser()))
                    .exchange()
                    .expectStatus()
                    .isNotFound()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("No resource found for this path.");
        }

        @Test
        void should_return_413_when_a_get_carries_a_9_kb_body() {
            ErrorResponseDTO body = restTestClient
                    .method(HttpMethod.GET)
                    .uri("/api/v1/users")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(confirmedUser()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("x".repeat(9 * 1024))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("The request body is too large.");
        }
    }

    @Nested
    class CostAndLeaks {

        @Test
        void should_run_exactly_2_sql_statements_when_a_full_page_of_20_users_with_pictures_is_listed() {
            User caller = confirmedUser();
            for (User user : usersOneSecondApart(25)) {
                giveProfilePicture(user);
            }

            String cookie = cookieOf(caller);
            List<UserListItemResponseDTO> items = new ArrayList<>();

            long statements = SqlStatementCounter.countDuring(entityManagerFactory, () -> items.addAll(
                    get("", cookie)
                            .expectStatus()
                            .isOk()
                            .expectBody(UserListResponseDTO.class)
                            .returnResult()
                            .getResponseBody()
                            .getItems()
            ));

            assertThat(items).hasSize(20);
            assertThat(items).allMatch(item -> item.getProfilePictureUrl() != null);
            assertThat(statements).isEqualTo(2);
        }

        @Test
        void should_run_exactly_1_sql_statement_when_the_page_is_empty() {
            String cookie = cookieOf(confirmedUser());

            long statements = SqlStatementCounter.countDuring(entityManagerFactory, () ->
                    get("", cookie)
                            .expectStatus()
                            .isOk());

            assertThat(statements).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(strings = {"?size=0", "?size=abc", "?cursor=garbage"})
        void should_carry_no_exception_package_sql_or_host_name_when_a_400_is_returned(String query) {
            String body = get(query, confirmedUser())
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body)
                    .doesNotContain("Exception", "com.peter_gerdzhikov", "org.springframework", "java.")
                    .doesNotContainIgnoringCase("select ")
                    .doesNotContainIgnoringCase("localhost");
        }

        @Test
        void should_keep_an_invalid_cursor_value_out_of_the_log_output(CapturedOutput output) {
            String cursor = "LEAKCHECKCURSORVALUE1234";

            get("?cursor=" + cursor, confirmedUser())
                    .expectStatus()
                    .isBadRequest();

            assertThat(output.getAll()).doesNotContain(cursor);
        }
    }
}
