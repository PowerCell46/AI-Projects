package com.peter_gerdzhikov.twitter_api_gateway.controllers.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.configurations.security.InternalApiSecretFilter;
import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalUserControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String USERS_PATH = "/internal/v1/users";

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Value("${app.internal-api.secret}")
    private String secret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Autowired
    private FollowRepository followRepository;

    @Nested
    class Secret {

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_api_gateway.controllers.internal.InternalUserControllerIntegrationTest#endpoints")
        void should_return_404_when_the_secret_header_is_missing(String path) throws Exception {
            mockMvc
                    .perform(get(path))
                    .andExpect(status().isNotFound());
        }

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_api_gateway.controllers.internal.InternalUserControllerIntegrationTest#endpoints")
        void should_return_404_when_the_secret_is_wrong(String path) throws Exception {
            mockMvc
                    .perform(get(path).header(InternalApiSecretFilter.SECRET_HEADER, "y".repeat(secret.length())))
                    .andExpect(status().isNotFound());
        }

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_api_gateway.controllers.internal.InternalUserControllerIntegrationTest#endpoints")
        void should_return_200_when_the_secret_is_right(String path) throws Exception {
            mockMvc
                    .perform(get(path).header(InternalApiSecretFilter.SECRET_HEADER, secret))
                    .andExpect(status().isOk());
        }

        @Test
        void should_answer_with_the_error_shape_of_an_unknown_path_when_the_secret_is_missing() throws Exception {
            MvcResult result = mockMvc
                    .perform(get(USERS_PATH).param("ids", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound())
                    .andReturn();

            JsonNode body = json(result);
            assertThat(body.get("status").asInt()).isEqualTo(404);
            assertThat(body.get("messages").get(0).asString()).isEqualTo("No resource found for this path.");
        }

        @Test
        void should_not_ask_for_a_login_when_the_secret_is_right() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH).param("ids", UUID.randomUUID().toString()))
                    .andExpect(status().isOk());
        }

        @Test
        void should_ignore_an_invalid_login_cookie_when_the_secret_is_right() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH)
                            .param("ids", UUID.randomUUID().toString())
                            .cookie(new Cookie(CookieFactory.COOKIE_NAME, "not-a-valid-token")))
                    .andExpect(status().isOk());
        }

        @Test
        void should_leave_a_public_route_open_when_no_secret_is_sent() throws Exception {
            mockMvc
                    .perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        void should_still_require_a_login_for_a_non_internal_route_when_the_secret_is_sent() throws Exception {
            mockMvc
                    .perform(internalGet("/api/v1/users/someone"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class FollowerIds {

        @Test
        void should_return_an_empty_page_when_the_user_is_unknown() throws Exception {
            JsonNode body = followerIds(UUID.randomUUID(), "", status().isOk());

            assertThat(body.get("ids")).isEmpty();
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_return_an_empty_page_when_the_user_has_no_followers() throws Exception {
            JsonNode body = followerIds(confirmedUser().getId(), "", status().isOk());

            assertThat(body.get("ids")).isEmpty();
        }

        @Test
        void should_return_only_ids_and_a_cursor_when_the_user_has_followers() throws Exception {
            User target = confirmedUser();
            followedBy(target, CREATED_AT);

            JsonNode body = followerIds(target.getId(), "", status().isOk());

            assertThat(body.propertyNames()).containsExactlyInAnyOrder("ids", "nextCursor");
        }

        @Test
        void should_return_the_follower_ids_newest_follow_first() throws Exception {
            User target = confirmedUser();
            User oldest = followedBy(target, CREATED_AT);
            User middle = followedBy(target, CREATED_AT.plusSeconds(1));
            User newest = followedBy(target, CREATED_AT.plusSeconds(2));

            JsonNode body = followerIds(target.getId(), "", status().isOk());

            assertThat(idsOf(body)).containsExactly(newest.getId().toString(), middle.getId().toString(), oldest.getId().toString());
        }

        @Test
        void should_return_only_the_followers_of_that_user() throws Exception {
            User target = confirmedUser();
            User follower = followedBy(target, CREATED_AT);
            followedBy(confirmedUser(), CREATED_AT);
            followRepository.insertIfAbsent(UUID.randomUUID(), target.getId(), confirmedUser().getId(), CREATED_AT);

            JsonNode body = followerIds(target.getId(), "", status().isOk());

            assertThat(idsOf(body)).containsExactly(follower.getId().toString());
        }

        @Test
        void should_return_every_follower_with_no_cursor_when_the_default_size_covers_them_all() throws Exception {
            User target = confirmedUser();
            for (int index = 0; index < 5; index++) {
                followedBy(target, CREATED_AT.plusSeconds(index));
            }

            JsonNode body = followerIds(target.getId(), "", status().isOk());

            assertThat(idsOf(body)).hasSize(5);
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_return_no_cursor_when_the_followers_exactly_fill_the_page() throws Exception {
            User target = confirmedUser();
            for (int index = 0; index < 3; index++) {
                followedBy(target, CREATED_AT.plusSeconds(index));
            }

            JsonNode body = followerIds(target.getId(), "?size=3", status().isOk());

            assertThat(idsOf(body)).hasSize(3);
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_visit_every_follower_exactly_once_when_walking_the_cursor() throws Exception {
            User target = confirmedUser();
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(followedBy(target, CREATED_AT.plusSeconds(index)).getId().toString());
            }

            List<String> seen = walk(target, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_visit_every_follower_exactly_once_when_every_follow_shares_one_timestamp() throws Exception {
            User target = confirmedUser();
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(followedBy(target, CREATED_AT).getId().toString());
            }

            List<String> seen = walk(target, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_return_200_when_the_size_is_1000() throws Exception {
            followerIds(confirmedUser().getId(), "?size=1000", status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "1001"})
        void should_return_400_when_the_size_is_out_of_range(String size) throws Exception {
            JsonNode body = followerIds(UUID.randomUUID(), "?size=" + size, status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Page size must be between 1 and 1000.");
        }

        @Test
        void should_return_400_when_the_size_is_not_a_number() throws Exception {
            followerIds(UUID.randomUUID(), "?size=many", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_cursor_is_invalid() throws Exception {
            JsonNode body = followerIds(UUID.randomUUID(), "?cursor=not-a-cursor", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Invalid cursor.");
        }

        @Test
        void should_return_400_when_the_user_id_is_not_a_uuid() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH + "/not-a-uuid/follower-ids"))
                    .andExpect(status().isBadRequest());
        }

        private List<String> walk(User target, int size) throws Exception {
            List<String> seen = new ArrayList<>();
            String query = "?size=" + size;
            while (true) {
                JsonNode page = followerIds(target.getId(), query, status().isOk());
                seen.addAll(idsOf(page));
                if (page.get("nextCursor").isNull()) {
                    return seen;
                }

                query = "?size=" + size + "&cursor=" + page.get("nextCursor").asString();
            }
        }
    }

    @Nested
    class Users {

        @Test
        void should_return_the_id_username_and_picture_url_and_nothing_else() throws Exception {
            DbFile picture = dbFileRepository.save(TestEntities.newDbFile());
            User user = confirmedUser();
            user.setProfilePicture(picture);
            userRepository.save(user);

            JsonNode body = users(List.of(user.getId().toString()), status().isOk());

            assertThat(body).hasSize(1);
            assertThat(body.get(0).propertyNames()).containsExactlyInAnyOrder("id", "username", "profilePictureUrl");
            assertThat(body.get(0).get("id").asString()).isEqualTo(user.getId().toString());
            assertThat(body.get(0).get("username").asString()).isEqualTo(user.getUsername());
            assertThat(body.get(0).get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/" + picture.getId());
        }

        @Test
        void should_return_a_null_picture_url_when_the_user_has_no_picture() throws Exception {
            User user = confirmedUser();

            JsonNode body = users(List.of(user.getId().toString()), status().isOk());

            assertThat(body.get(0).get("profilePictureUrl").isNull()).isTrue();
        }

        @Test
        void should_return_only_the_known_users_when_some_ids_are_unknown() throws Exception {
            User first = confirmedUser();
            User second = confirmedUser();

            JsonNode body = users(List.of(first.getId().toString(), UUID.randomUUID().toString(), second.getId().toString()), status().isOk());

            assertThat(idsOf(body)).containsExactlyInAnyOrder(first.getId().toString(), second.getId().toString());
        }

        @Test
        void should_return_an_empty_list_when_no_id_is_known() throws Exception {
            assertThat(users(List.of(UUID.randomUUID().toString()), status().isOk())).isEmpty();
        }

        @Test
        void should_leave_out_a_user_who_has_not_confirmed_the_account() throws Exception {
            User pending = TestEntities.newUser();
            pending.setEnabled(false);
            userRepository.save(pending);

            assertThat(users(List.of(pending.getId().toString()), status().isOk())).isEmpty();
        }

        @Test
        void should_return_each_user_once_when_an_id_is_repeated() throws Exception {
            User user = confirmedUser();
            String id = user.getId().toString();

            assertThat(idsOf(users(List.of(id, id, id), status().isOk()))).containsExactly(id);
        }

        @Test
        void should_accept_a_comma_separated_list_when_ids_are_joined() throws Exception {
            User first = confirmedUser();
            User second = confirmedUser();

            MvcResult result = mockMvc
                    .perform(internalGet(USERS_PATH).param("ids", first.getId() + "," + second.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(idsOf(json(result))).containsExactlyInAnyOrder(first.getId().toString(), second.getId().toString());
        }

        @Test
        void should_return_200_when_exactly_100_ids_are_given() throws Exception {
            users(randomIds(100), status().isOk());
        }

        @Test
        void should_return_400_when_101_ids_are_given() throws Exception {
            JsonNode body = users(randomIds(101), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Provide between 1 and 100 user ids.");
        }

        @Test
        void should_return_400_when_the_ids_parameter_is_empty() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH).param("ids", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_ids_parameter_is_missing() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_an_id_is_not_a_uuid() throws Exception {
            users(List.of(UUID.randomUUID().toString(), "not-a-uuid"), status().isBadRequest());
        }
    }

    @Nested
    class Follows {

        @Test
        void should_return_200_with_following_true_when_the_follower_follows_the_followee() throws Exception {
            User follower = confirmedUser();
            User followee = confirmedUser();
            follow(follower, followee);

            assertThat(isFollowing(follower.getId(), followee.getId())).isTrue();
        }

        @Test
        void should_return_200_with_following_false_when_the_follower_does_not_follow_the_followee() throws Exception {
            assertThat(isFollowing(confirmedUser().getId(), confirmedUser().getId())).isFalse();
        }

        @Test
        void should_return_200_with_following_false_when_the_follow_runs_the_other_way() throws Exception {
            User follower = confirmedUser();
            User followee = confirmedUser();
            follow(follower, followee);

            assertThat(isFollowing(followee.getId(), follower.getId())).isFalse();
        }

        @Test
        void should_return_200_with_following_false_when_the_follow_was_removed() throws Exception {
            User follower = confirmedUser();
            User followee = confirmedUser();
            follow(follower, followee);
            followRepository.deleteByPair(follower.getId(), followee.getId());

            assertThat(isFollowing(follower.getId(), followee.getId())).isFalse();
        }

        @Test
        void should_return_200_with_following_false_when_the_ids_are_unknown() throws Exception {
            assertThat(isFollowing(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        }

        @Test
        void should_return_200_with_following_false_when_both_ids_are_the_same_user() throws Exception {
            UUID userId = confirmedUser().getId();

            assertThat(isFollowing(userId, userId)).isFalse();
        }

        @Test
        void should_return_404_with_the_error_shape_of_an_unknown_path_when_the_secret_is_missing() throws Exception {
            User follower = confirmedUser();
            User followee = confirmedUser();
            follow(follower, followee);

            MvcResult result = mockMvc
                    .perform(get(followPath(follower.getId(), followee.getId())))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("No resource found for this path.");
        }

        @Test
        void should_return_404_when_the_secret_is_wrong_even_though_the_follow_exists() throws Exception {
            User follower = confirmedUser();
            User followee = confirmedUser();
            follow(follower, followee);

            MvcResult result = mockMvc
                    .perform(get(followPath(follower.getId(), followee.getId()))
                            .header(InternalApiSecretFilter.SECRET_HEADER, "y".repeat(secret.length())))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("No resource found for this path.");
        }

        @Test
        void should_return_400_when_the_follower_id_is_not_a_uuid() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH + "/not-a-uuid/follows/" + UUID.randomUUID()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_followee_id_is_not_a_uuid() throws Exception {
            mockMvc
                    .perform(internalGet(USERS_PATH + "/" + UUID.randomUUID() + "/follows/not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        private boolean isFollowing(UUID followerId, UUID followeeId) throws Exception {
            MvcResult result = mockMvc
                    .perform(internalGet(followPath(followerId, followeeId)))
                    .andExpect(status().isOk())
                    .andReturn();

            return json(result).get("following").asBoolean();
        }

        private void follow(User follower, User followee) {
            followRepository.insertIfAbsent(UUID.randomUUID(), follower.getId(), followee.getId(), CREATED_AT);
        }

        private String followPath(UUID followerId, UUID followeeId) {
            return USERS_PATH + "/" + followerId + "/follows/" + followeeId;
        }
    }

    static Stream<String> endpoints() {
        return Stream.of(
                USERS_PATH + "/" + UUID.randomUUID() + "/follower-ids",
                USERS_PATH + "?ids=" + UUID.randomUUID(),
                USERS_PATH + "/" + UUID.randomUUID() + "/follows/" + UUID.randomUUID()
        );
    }

    private MockHttpServletRequestBuilder internalGet(String path) {
        return get(path).header(InternalApiSecretFilter.SECRET_HEADER, secret);
    }

    private JsonNode followerIds(UUID userId, String query, ResultMatcher expected) throws Exception {
        return json(mockMvc
                .perform(internalGet(USERS_PATH + "/" + userId + "/follower-ids" + query))
                .andExpect(expected)
                .andReturn());
    }

    private JsonNode users(List<String> ids, ResultMatcher expected) throws Exception {
        return json(mockMvc
                .perform(internalGet(USERS_PATH).param("ids", ids.toArray(String[]::new)))
                .andExpect(expected)
                .andReturn());
    }

    private List<String> idsOf(JsonNode node) {
        JsonNode ids = node.isArray() ? node : node.get("ids");

        return ids
                .valueStream()
                .map(element -> element.isString() ? element.asString() : element.get("id").asString())
                .toList();
    }

    private List<String> randomIds(int count) {
        return Stream
                .generate(UUID::randomUUID)
                .map(UUID::toString)
                .limit(count)
                .toList();
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private User followedBy(User target, Instant createdAt) {
        User follower = confirmedUser();
        followRepository.insertIfAbsent(UUID.randomUUID(), follower.getId(), target.getId(), createdAt);

        return follower;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
