package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.MutableClock;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class FollowControllerIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    private User confirmedUser() {
        return saveUser(true);
    }

    private User saveUser(boolean enabled) {
        User user = TestEntities.newUser();
        user.setEnabled(enabled);

        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String cookieOf(User user) {
        return tokenService.mint(user);
    }

    private RestTestClient.ResponseSpec follow(String username, String cookie) {
        return send(HttpMethod.PUT, username, cookie);
    }

    private RestTestClient.ResponseSpec unfollow(String username, String cookie) {
        return send(HttpMethod.DELETE, username, cookie);
    }

    private RestTestClient.ResponseSpec send(HttpMethod method, String username, String cookie) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient
                .method(method)
                .uri("/api/v1/users/{username}/follow", username);
        if (cookie != null) {
            request.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        return request.exchange();
    }

    private void followSuccessfully(User follower, User target) {
        follow(target.getUsername(), cookieOf(follower))
                .expectStatus()
                .isNoContent();
    }

    private int followRows(User follower, User target) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM follows WHERE follower_id = ? AND following_id = ?",
                Integer.class,
                follower.getId(),
                target.getId()
        );
    }

    private int followRowsOf(User follower) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM follows WHERE follower_id = ?",
                Integer.class,
                follower.getId()
        );
    }

    private void assertCounts(User user, long followers, long following) {
        User found = reload(user);

        assertThat(found.getFollowersCount()).isEqualTo(followers);
        assertThat(found.getFollowingCount()).isEqualTo(following);
    }

    private ProfileResponseDTO profileOf(User target, User viewer) {
        return restTestClient
                .get()
                .uri("/api/v1/users/{username}", target.getUsername())
                .cookie(CookieFactory.COOKIE_NAME, cookieOf(viewer))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ProfileResponseDTO.class)
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

    @Nested
    class Follow {

        @Test
        void should_return_204_and_store_the_row_and_increment_both_counts_when_the_target_exists() {
            User follower = confirmedUser();
            User target = confirmedUser();

            follow(target.getUsername(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertThat(followRows(follower, target)).isEqualTo(1);
            assertCounts(target, 1, 0);
            assertCounts(follower, 0, 1);
        }

        @Test
        void should_return_204_and_keep_the_counts_when_the_follow_is_repeated() {
            User follower = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(follower, target);

            followSuccessfully(follower, target);

            assertThat(followRows(follower, target)).isEqualTo(1);
            assertCounts(target, 1, 0);
            assertCounts(follower, 0, 1);
        }

        @Test
        void should_return_204_when_the_username_differs_only_in_case() {
            User follower = confirmedUser();
            User target = confirmedUser();

            follow(target.getUsername().toUpperCase(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertThat(followRows(follower, target)).isEqualTo(1);
        }

        @Test
        void should_return_400_and_store_nothing_when_the_user_follows_themselves() {
            User user = confirmedUser();

            assertError(follow(user.getUsername(), cookieOf(user)), 400, "You cannot follow yourself.");

            assertThat(followRowsOf(user)).isZero();
            assertCounts(user, 0, 0);
        }

        @Test
        void should_return_400_when_the_user_follows_themselves_with_a_username_in_another_case() {
            User user = confirmedUser();

            assertError(follow(user.getUsername().toUpperCase(), cookieOf(user)), 400, "You cannot follow yourself.");

            assertThat(followRowsOf(user)).isZero();
        }

        @Test
        void should_return_404_when_the_target_is_unknown() {
            User follower = confirmedUser();

            assertError(follow(TestUsers.unique().getUsername(), cookieOf(follower)), 404, "User not found.");

            assertThat(followRowsOf(follower)).isZero();
            assertCounts(follower, 0, 0);
        }

        @Test
        void should_return_404_when_the_target_is_unconfirmed() {
            User follower = confirmedUser();
            User pending = saveUser(false);

            assertError(follow(pending.getUsername(), cookieOf(follower)), 404, "User not found.");

            assertThat(followRows(follower, pending)).isZero();
            assertCounts(pending, 0, 0);
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            User target = confirmedUser();

            follow(target.getUsername(), null)
                    .expectStatus()
                    .isUnauthorized();
        }
    }

    @Nested
    class Unfollow {

        @Test
        void should_return_204_and_remove_the_row_and_decrement_both_counts_when_the_follow_exists() {
            User follower = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(follower, target);

            unfollow(target.getUsername(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertThat(followRows(follower, target)).isZero();
            assertCounts(target, 0, 0);
            assertCounts(follower, 0, 0);
        }

        @Test
        void should_return_204_and_keep_the_counts_when_the_unfollow_is_repeated() {
            User follower = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(follower, target);
            unfollow(target.getUsername(), cookieOf(follower));

            unfollow(target.getUsername(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertCounts(target, 0, 0);
            assertCounts(follower, 0, 0);
        }

        @Test
        void should_return_204_and_keep_the_counts_when_the_user_never_followed_the_target() {
            User follower = confirmedUser();
            User target = confirmedUser();

            unfollow(target.getUsername(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertCounts(target, 0, 0);
            assertCounts(follower, 0, 0);
        }

        @Test
        void should_return_204_when_the_username_differs_only_in_case() {
            User follower = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(follower, target);

            unfollow(target.getUsername().toUpperCase(), cookieOf(follower))
                    .expectStatus()
                    .isNoContent();

            assertThat(followRows(follower, target)).isZero();
        }

        @Test
        void should_return_400_when_the_user_unfollows_themselves() {
            User user = confirmedUser();

            assertError(unfollow(user.getUsername(), cookieOf(user)), 400, "You cannot follow yourself.");
        }

        @Test
        void should_return_404_when_the_target_is_unknown() {
            User follower = confirmedUser();

            assertError(unfollow(TestUsers.unique().getUsername(), cookieOf(follower)), 404, "User not found.");
        }

        @Test
        void should_return_404_when_the_target_is_unconfirmed() {
            User follower = confirmedUser();
            User pending = saveUser(false);

            assertError(unfollow(pending.getUsername(), cookieOf(follower)), 404, "User not found.");
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            User target = confirmedUser();

            unfollow(target.getUsername(), null)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_keep_the_other_users_follows_when_one_follow_is_removed() {
            User follower = confirmedUser();
            User removed = confirmedUser();
            User kept = confirmedUser();
            followSuccessfully(follower, removed);
            followSuccessfully(follower, kept);
            followSuccessfully(kept, follower);

            unfollow(removed.getUsername(), cookieOf(follower));

            assertThat(followRows(follower, kept)).isEqualTo(1);
            assertThat(followRows(kept, follower)).isEqualTo(1);
            assertCounts(follower, 1, 1);
            assertCounts(kept, 1, 1);
            assertCounts(removed, 0, 0);
        }
    }

    @Nested
    class Profile {

        @Test
        void should_return_zero_counts_and_not_followed_when_the_user_has_no_follows() {
            User target = confirmedUser();

            ProfileResponseDTO profile = profileOf(target, confirmedUser());

            assertThat(profile.getFollowersCount()).isZero();
            assertThat(profile.getFollowingCount()).isZero();
            assertThat(profile.isFollowedByMe()).isFalse();
        }

        @Test
        void should_show_the_followers_count_on_the_target_and_the_following_count_on_the_follower_when_a_follow_exists() {
            User follower = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(follower, target);
            User viewer = confirmedUser();

            ProfileResponseDTO targetProfile = profileOf(target, viewer);
            ProfileResponseDTO followerProfile = profileOf(follower, viewer);

            assertThat(targetProfile.getFollowersCount()).isEqualTo(1);
            assertThat(targetProfile.getFollowingCount()).isZero();
            assertThat(followerProfile.getFollowersCount()).isZero();
            assertThat(followerProfile.getFollowingCount()).isEqualTo(1);
        }

        @Test
        void should_return_followed_by_me_true_when_the_viewer_follows_the_target() {
            User viewer = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(viewer, target);

            assertThat(profileOf(target, viewer).isFollowedByMe()).isTrue();
        }

        @Test
        void should_return_followed_by_me_false_when_only_the_target_follows_the_viewer() {
            User viewer = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(target, viewer);

            assertThat(profileOf(target, viewer).isFollowedByMe()).isFalse();
        }

        @Test
        void should_return_followed_by_me_true_on_both_sides_when_two_users_follow_each_other() {
            User first = confirmedUser();
            User second = confirmedUser();
            followSuccessfully(first, second);
            followSuccessfully(second, first);

            assertThat(profileOf(second, first).isFollowedByMe()).isTrue();
            assertThat(profileOf(first, second).isFollowedByMe()).isTrue();
        }

        @Test
        void should_return_followed_by_me_false_when_the_viewer_opens_their_own_profile() {
            User viewer = confirmedUser();
            followSuccessfully(confirmedUser(), viewer);

            ProfileResponseDTO profile = profileOf(viewer, viewer);

            assertThat(profile.isFollowedByMe()).isFalse();
            assertThat(profile.getFollowersCount()).isEqualTo(1);
        }

        @Test
        void should_update_the_counts_and_followed_by_me_when_the_follow_is_removed() {
            User viewer = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(viewer, target);
            unfollow(target.getUsername(), cookieOf(viewer));

            ProfileResponseDTO profile = profileOf(target, viewer);

            assertThat(profile.getFollowersCount()).isZero();
            assertThat(profile.isFollowedByMe()).isFalse();
            assertThat(profileOf(viewer, viewer).getFollowingCount()).isZero();
        }

        @Test
        void should_return_the_counts_and_followed_by_me_in_the_profile_shape_when_the_user_exists() {
            User viewer = confirmedUser();
            User target = confirmedUser();
            followSuccessfully(viewer, target);

            restTestClient
                    .get()
                    .uri("/api/v1/users/{username}", target.getUsername())
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(viewer))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.followersCount").isEqualTo(1)
                    .jsonPath("$.followingCount").isEqualTo(0)
                    .jsonPath("$.followedByMe").isEqualTo(true);
        }
    }

    @Nested
    class Lists {

        @BeforeEach
        void resetClock() {
            mutableClock.reset();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_the_items_newest_first_when_the_list_has_several_entries(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 3, true);

            FollowListResponseDTO page = pageOf(list, target, confirmedUser(), "");

            assertThat(usernamesOf(page)).containsExactly(
                    members.get(2).getUsername(),
                    members.get(1).getUsername(),
                    members.get(0).getUsername()
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_items_with_id_username_bio_picture_url_and_followed_by_me_when_the_list_is_not_empty(String list) {
            User target = confirmedUser();
            User member = linkMembers(list, target, 1, true).getFirst();
            member.setBio("Hello");
            userRepository.save(member);

            Map<String, Object> item = firstItemOf(list, target);

            assertThat(item.keySet()).containsExactlyInAnyOrder("id", "username", "bio", "profilePictureUrl", "followedByMe");
            assertThat(item.get("id")).isEqualTo(member.getId().toString());
            assertThat(item.get("username")).isEqualTo(member.getUsername());
            assertThat(item.get("bio")).isEqualTo("Hello");
            assertThat(item.get("profilePictureUrl")).isNull();
            assertThat(item.get("followedByMe")).isEqualTo(false);
        }

        @Test
        void should_list_only_the_users_who_follow_the_target_when_the_followers_are_requested() {
            User target = confirmedUser();
            User follower = linkMembers("followers", target, 1, true).getFirst();
            linkMembers("following", target, 1, true);

            assertThat(usernamesOf(pageOf("followers", target, target, ""))).containsExactly(follower.getUsername());
        }

        @Test
        void should_list_only_the_users_the_target_follows_when_the_following_are_requested() {
            User target = confirmedUser();
            linkMembers("followers", target, 1, true);
            User followed = linkMembers("following", target, 1, true).getFirst();

            assertThat(usernamesOf(pageOf("following", target, target, ""))).containsExactly(followed.getUsername());
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_20_items_and_a_next_cursor_when_no_size_is_given_and_more_exist(String list) {
            User target = confirmedUser();
            linkMembers(list, target, 21, true);

            FollowListResponseDTO page = pageOf(list, target, confirmedUser(), "");

            assertThat(page.getItems()).hasSize(20);
            assertThat(page.getNextCursor()).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_every_entry_exactly_once_when_the_pages_are_followed_to_the_end(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 25, true);

            List<String> seen = collectAll(list, target, confirmedUser(), 10, () -> { });

            assertThat(seen).containsExactlyElementsOf(newestFirst(members));
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_every_entry_exactly_once_when_new_follows_are_inserted_between_page_requests(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 6, true);

            List<String> seen = collectAll(list, target, confirmedUser(), 2, () -> {
                mutableClock.advance(Duration.ofSeconds(1));
                linkMembers(list, target, 1, false);
            });

            assertThat(seen).containsExactlyElementsOf(newestFirst(members));
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_every_entry_exactly_once_when_all_follows_share_the_same_timestamp(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 7, false);

            List<String> seen = collectAll(list, target, confirmedUser(), 3, () -> { });

            assertThat(seen).hasSize(7);
            assertThat(seen).doesNotHaveDuplicates();
            assertThat(seen).containsExactlyInAnyOrderElementsOf(usernamesOf(members));
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_a_null_next_cursor_when_the_page_is_the_last_one(String list) {
            User target = confirmedUser();
            linkMembers(list, target, 3, true);

            FollowListResponseDTO page = pageOf(list, target, confirmedUser(), "?size=10");

            assertThat(page.getItems()).hasSize(3);
            assertThat(page.getNextCursor()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_a_null_next_cursor_when_the_entries_exactly_fill_the_page(String list) {
            User target = confirmedUser();
            linkMembers(list, target, 4, true);

            FollowListResponseDTO page = pageOf(list, target, confirmedUser(), "?size=4");

            assertThat(page.getItems()).hasSize(4);
            assertThat(page.getNextCursor()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_empty_items_and_a_null_next_cursor_when_the_list_is_empty(String list) {
            FollowListResponseDTO page = pageOf(list, confirmedUser(), confirmedUser(), "");

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_mark_followed_by_me_per_item_when_the_viewer_follows_only_some_of_them(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 3, true);
            User viewer = confirmedUser();
            followSuccessfully(viewer, members.get(1));

            FollowListResponseDTO page = pageOf(list, target, viewer, "");

            assertThat(page.getItems())
                    .extracting(FollowListItemResponseDTO::getUsername, FollowListItemResponseDTO::isFollowedByMe)
                    .containsExactly(
                            tuple(members.get(2).getUsername(), false),
                            tuple(members.get(1).getUsername(), true),
                            tuple(members.get(0).getUsername(), false)
                    );
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_drop_the_entry_when_the_follow_is_removed(String list) {
            User target = confirmedUser();
            List<User> members = linkMembers(list, target, 2, true);

            unlink(list, target, members.getFirst());

            assertThat(usernamesOf(pageOf(list, target, confirmedUser(), "")))
                    .containsExactly(members.getLast().getUsername());
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_200_when_the_size_is_1_or_100(String list) {
            User target = confirmedUser();
            User viewer = confirmedUser();

            get(list, target.getUsername(), "?size=1", cookieOf(viewer)).expectStatus().isOk();
            get(list, target.getUsername(), "?size=100", cookieOf(viewer)).expectStatus().isOk();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_400_when_the_size_is_0(String list) {
            User target = confirmedUser();

            assertError(get(list, target.getUsername(), "?size=0", cookieOf(target)), 400, InvalidPageSizeException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_400_when_the_size_is_101(String list) {
            User target = confirmedUser();

            assertError(get(list, target.getUsername(), "?size=101", cookieOf(target)), 400, InvalidPageSizeException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_400_when_the_size_is_not_a_number(String list) {
            User target = confirmedUser();

            assertError(get(list, target.getUsername(), "?size=abc", cookieOf(target)), 400, "Malformed request parameter.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_400_when_the_cursor_is_garbage(String list) {
            User target = confirmedUser();

            assertError(get(list, target.getUsername(), "?cursor=not-a-cursor!", cookieOf(target)), 400, InvalidCursorException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_400_when_the_cursor_is_valid_base64url_but_has_the_wrong_content(String list) {
            User target = confirmedUser();
            String cursor = Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

            assertError(get(list, target.getUsername(), "?cursor=" + cursor, cookieOf(target)), 400, InvalidCursorException.MESSAGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_200_when_the_username_differs_only_in_case(String list) {
            User target = confirmedUser();

            get(list, target.getUsername().toUpperCase(), "", cookieOf(target))
                    .expectStatus()
                    .isOk();
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_404_when_the_user_is_unknown(String list) {
            assertError(get(list, TestUsers.unique().getUsername(), "", cookieOf(confirmedUser())), 404, "User not found.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_404_when_the_user_is_unconfirmed(String list) {
            User pending = saveUser(false);

            assertError(get(list, pending.getUsername(), "", cookieOf(confirmedUser())), 404, "User not found.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"followers", "following"})
        void should_return_401_when_there_is_no_cookie(String list) {
            get(list, confirmedUser().getUsername(), "", null)
                    .expectStatus()
                    .isUnauthorized();
        }

        private RestTestClient.ResponseSpec get(String list, String username, String query, String cookie) {
            RestTestClient.RequestHeadersSpec<?> request = restTestClient
                    .get()
                    .uri("/api/v1/users/" + username + "/" + list + query);
            if (cookie != null) {
                request.cookie(CookieFactory.COOKIE_NAME, cookie);
            }

            return request.exchange();
        }

        private FollowListResponseDTO pageOf(String list, User target, User viewer, String query) {
            return get(list, target.getUsername(), query, cookieOf(viewer))
                    .expectStatus()
                    .isOk()
                    .expectBody(FollowListResponseDTO.class)
                    .returnResult()
                    .getResponseBody();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> firstItemOf(String list, User target) {
            Map<?, ?> body = get(list, target.getUsername(), "", cookieOf(confirmedUser()))
                    .expectStatus()
                    .isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            return (Map<String, Object>) ((List<?>) body.get("items")).getFirst();
        }

        /**
         * Pages to the end and returns the usernames in the order seen. {@code betweenPages} runs after
         * every page that has a successor.
         */
        private List<String> collectAll(String list, User target, User viewer, int size, Runnable betweenPages) {
            List<String> seen = new ArrayList<>();
            String cursor = null;
            do {
                String query = "?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
                FollowListResponseDTO page = pageOf(list, target, viewer, query);
                seen.addAll(usernamesOf(page));
                cursor = page.getNextCursor();
                if (cursor != null) {
                    betweenPages.run();
                }
            } while (cursor != null);

            return seen;
        }

        /**
         * Creates {@code count} users tied to the target: followers of it for {@code followers}, users it
         * follows for {@code following}. With {@code advanceClock}, each follow lands a second after the last.
         */
        private List<User> linkMembers(String list, User target, int count, boolean advanceClock) {
            List<User> members = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                User member = confirmedUser();
                if (advanceClock) {
                    mutableClock.advance(Duration.ofSeconds(1));
                }
                if (list.equals("followers")) {
                    followSuccessfully(member, target);

                } else {
                    followSuccessfully(target, member);
                }
                members.add(member);
            }

            return members;
        }

        private void unlink(String list, User target, User member) {
            if (list.equals("followers")) {
                unfollow(target.getUsername(), cookieOf(member));

            } else {
                unfollow(member.getUsername(), cookieOf(target));
            }
        }

        private List<String> usernamesOf(FollowListResponseDTO page) {
            return page
                    .getItems()
                    .stream()
                    .map(FollowListItemResponseDTO::getUsername)
                    .toList();
        }

        private List<String> usernamesOf(List<User> members) {
            return members
                    .stream()
                    .map(User::getUsername)
                    .toList();
        }

        private List<String> newestFirst(List<User> members) {
            return usernamesOf(members.reversed());
        }
    }
}
