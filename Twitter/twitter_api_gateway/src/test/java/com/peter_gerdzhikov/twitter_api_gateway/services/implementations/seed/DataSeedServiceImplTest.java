package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;
import com.peter_gerdzhikov.twitter_api_gateway.configurations.SeedProperties;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows.FollowService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.LikeSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ProfilePictureSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ReplySeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.TweetSeedService;

@ExtendWith(MockitoExtension.class)
class DataSeedServiceImplTest {

    private static final String PASSWORD = "Str0ng-seed-password";

    private static final Map<String, String> REAL_PEOPLE_PASSWORDS = Map.of(
            "stiliyan", "Stiliyan-0wn-password",
            "kristian", "Kristian-0wn-password",
            "gosho", "Gosho-0wn-password",
            "gabi", "Gabi-0wn-password"
    );

    @Mock
    private FollowService followService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LikeSeedService likeSeedService;

    @Mock
    private ReplySeedService replySeedService;

    @Mock
    private TweetSeedService tweetSeedService;

    @Mock
    private ProfilePictureSeedService profilePictureSeedService;

    private DataSeedServiceImpl seedService;

    @BeforeEach
    void setUp() {
        seedService = newSeedService(PASSWORD);
    }

    private DataSeedServiceImpl newSeedService(String password) {
        return newSeedService(password, REAL_PEOPLE_PASSWORDS);
    }

    private DataSeedServiceImpl newSeedService(String password, Map<String, String> realPeoplePasswords) {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.setPasswords(realPeoplePasswords);

        return new DataSeedServiceImpl(
                password, seedProperties, followService, userRepository, passwordEncoder,
                likeSeedService, replySeedService, tweetSeedService, profilePictureSeedService);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "Short1aA",
                "Password123",
                "NoDigitsInThisPassword",
                "nouppercase-or-digit-here",
                "NOLOWERCASE1234567",
                "12345678901234567890"
        })
        void should_refuse_to_start_when_the_password_is_weak(String weakPassword) {
            assertThatThrownBy(() -> newSeedService(weakPassword))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SEED_PASSWORD");
        }

        @Test
        void should_refuse_to_start_when_the_password_is_longer_than_bcrypt_reads() {
            String tooLong = "Aa1" + "x".repeat(70);

            assertThatThrownBy(() -> newSeedService(tooLong))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("72 bytes");
        }

        @Test
        void should_not_put_the_password_in_the_message_when_it_is_refused() {
            String weakPassword = "Weak1Pw";

            assertThatThrownBy(() -> newSeedService(weakPassword))
                    .hasMessageNotContaining(weakPassword);
        }

        @ParameterizedTest
        @ValueSource(strings = {"stiliyan", "kristian", "gosho", "gabi"})
        void should_refuse_to_start_when_a_real_persons_password_is_missing(String username) {
            Map<String, String> missingOne = new HashMap<>(REAL_PEOPLE_PASSWORDS);
            missingOne.remove(username);

            assertThatThrownBy(() -> newSeedService(PASSWORD, missingOne))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SEED_PASSWORD_" + username.toUpperCase());
        }

        @Test
        void should_refuse_to_start_when_a_real_persons_password_is_weak() {
            Map<String, String> weakOne = new HashMap<>(REAL_PEOPLE_PASSWORDS);
            weakOne.put("gosho", "weak");

            assertThatThrownBy(() -> newSeedService(PASSWORD, weakOne))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SEED_PASSWORD_GOSHO")
                    .hasMessageNotContaining("weak");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Aa1xxxxxxxxx",
                "Passw0rd-with-symbols-!?#",
                "Aa1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        })
        void should_accept_the_password_when_it_is_long_enough_and_has_all_three_classes(String strongPassword) {
            assertThatCode(() -> newSeedService(strongPassword))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("seedIfEmpty")
    class SeedIfEmpty {

        @Test
        void should_do_nothing_when_a_user_already_exists() {
            when(userRepository.count()).thenReturn(1L);

            boolean seeded = seedService.seedIfEmpty();

            assertThat(seeded).isFalse();
            verify(userRepository, never()).saveAll(any());
            verifyNoInteractions(followService, likeSeedService, replySeedService, tweetSeedService, profilePictureSeedService);
        }

        @Test
        void should_save_the_twelve_confirmed_users_when_the_database_is_empty() {
            stubEmptyDatabase();

            seedService.seedIfEmpty();

            ArgumentCaptor<List<User>> saved = ArgumentCaptor.captor();
            verify(userRepository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(User::getUsername)
                    .containsExactlyElementsOf(DataSeedServiceImpl.USERNAMES);
            assertThat(saved.getValue())
                    .allMatch(User::isEnabled)
                    .allMatch(user -> user.getEmail().endsWith("@seed.local"))
                    .allMatch(user -> user.getPassword().equals("hash"));
        }

        @Test
        void should_follow_without_queueing_any_event_and_without_following_oneself() {
            List<User> saved = stubEmptyDatabase();

            seedService.seedIfEmpty();

            ArgumentCaptor<UUID> followerIds = ArgumentCaptor.captor();
            ArgumentCaptor<String> targets = ArgumentCaptor.captor();
            verify(followService, atLeastOnce()).followWithoutEvent(followerIds.capture(), targets.capture());
            Map<UUID, String> namesById = saved
                    .stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));
            verify(followService, never()).follow(any(), anyString());
            for (int i = 0; i < targets.getAllValues().size(); i++) {
                assertThat(namesById.get(followerIds.getAllValues().get(i)))
                        .isNotEqualTo(targets.getAllValues().get(i));
            }
        }

        @Test
        void should_hand_every_user_id_to_the_tweet_seeder() {
            List<User> saved = stubEmptyDatabase();

            boolean seeded = seedService.seedIfEmpty();

            assertThat(seeded).isTrue();
            verify(tweetSeedService).seedTweets(userIdsOf(saved));
        }

        @Test
        void should_give_the_saved_users_their_pictures() {
            List<User> saved = stubEmptyDatabase();

            seedService.seedIfEmpty();

            verify(profilePictureSeedService).seedProfilePictures(saved);
        }

        @Test
        void should_like_the_tweets_that_the_tweet_seeder_created() {
            List<User> saved = stubEmptyDatabase();
            List<SeededTweetClientDTO> tweets = List.of(new SeededTweetClientDTO());
            when(tweetSeedService.seedTweets(userIdsOf(saved))).thenReturn(tweets);

            seedService.seedIfEmpty();

            verify(likeSeedService).seedLikes(userIdsOf(saved), tweets);
        }

        @Test
        void should_reply_to_the_tweets_that_the_tweet_seeder_created() {
            List<User> saved = stubEmptyDatabase();
            List<SeededTweetClientDTO> tweets = List.of(new SeededTweetClientDTO());
            when(tweetSeedService.seedTweets(userIdsOf(saved))).thenReturn(tweets);

            seedService.seedIfEmpty();

            verify(replySeedService).seedReplies(userIdsOf(saved), tweets);
        }

        @Test
        void should_hash_each_real_persons_own_password_and_the_shared_one_for_everyone_else() {
            stubEmptyDatabase();
            when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));

            seedService.seedIfEmpty();

            ArgumentCaptor<List<User>> saved = ArgumentCaptor.captor();
            verify(userRepository).saveAll(saved.capture());
            Map<String, String> hashesByUsername = saved.getValue()
                    .stream()
                    .collect(Collectors.toMap(User::getUsername, User::getPassword));
            for (String realPerson : DataSeedServiceImpl.REAL_PEOPLE) {
                assertThat(hashesByUsername.get(realPerson))
                        .isEqualTo("hash:" + REAL_PEOPLE_PASSWORDS.get(realPerson.toLowerCase()));
            }
            assertThat(hashesByUsername.get("Hristo")).isEqualTo("hash:" + PASSWORD);
            assertThat(hashesByUsername.get("Gabi2")).isEqualTo("hash:" + PASSWORD);
        }

        private List<UUID> userIdsOf(List<User> users) {
            return users
                    .stream()
                    .map(User::getId)
                    .toList();
        }

        private List<User> stubEmptyDatabase() {
            when(userRepository.count()).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            List<User> saved = DataSeedServiceImpl.USERNAMES
                    .stream()
                    .map(this::newSavedUser)
                    .toList();
            when(userRepository.saveAll(any())).thenReturn(saved);

            return saved;
        }

        private User newSavedUser(String username) {
            User user = User.builder()
                    .username(username)
                    .build();
            user.setId(UUID.randomUUID());

            return user;
        }
    }
}
