package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows.FollowService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.DataSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.LikeSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ProfilePictureSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ReplySeedService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.TweetSeedService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeedServiceImpl implements DataSeedService {

    static final String EMAIL_DOMAIN = "seed.local";

    static final List<String> USERNAMES = List.of(
            "Stiliyan", "Kristian", "Kristyian", "Gosho", "Gabi", "Gabi2",
            "Hristo", "Bobkata", "Atanas", "Stanimir", "Bogdan", "Kaloyan"
    );

    private static final long RANDOM_SEED = 2026;

    private static final int MAX_PASSWORD_BYTES = 72;

    private static final int MIN_PASSWORD_LENGTH = 12;

    private static final double FOLLOW_PROBABILITY = 0.35;

    private static final String BIO = "Demo account created by the database seeder.";

    private static final Pattern PASSWORD_CLASSES = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$");

    private final String password;

    private final FollowService followService;

    private final UserRepository userRepository;

    private final LikeSeedService likeSeedService;

    private final PasswordEncoder passwordEncoder;

    private final ReplySeedService replySeedService;

    private final TweetSeedService tweetSeedService;

    private final ProfilePictureSeedService profilePictureSeedService;

    public DataSeedServiceImpl(
            @Value("${app.seed.password}") String password,
            FollowService followService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LikeSeedService likeSeedService,
            ReplySeedService replySeedService,
            TweetSeedService tweetSeedService,
            ProfilePictureSeedService profilePictureSeedService
    ) {
        this.password = requireStrongPassword(password);
        this.followService = followService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.likeSeedService = likeSeedService;
        this.replySeedService = replySeedService;
        this.tweetSeedService = tweetSeedService;
        this.profilePictureSeedService = profilePictureSeedService;
    }

    @Override
    public boolean seedIfEmpty() {
        if (userRepository.count() > 0) {
            return false;
        }

        List<User> users = userRepository.saveAll(newUsers());
        int follows = seedFollows(users);
        log.info("Seeded {} users and {} follows.", users.size(), follows);

        profilePictureSeedService.seedProfilePictures(users);

        List<UUID> userIds = users
                .stream()
                .map(User::getId)
                .toList();
        List<SeededTweetClientDTO> tweets = tweetSeedService.seedTweets(userIds);
        replySeedService.seedReplies(userIds, tweets);
        likeSeedService.seedLikes(userIds, tweets);

        return true;
    }

    /**
     * The demo users are real accounts with well-known usernames, so their shared password has to be a secret of
     * the deployment. The message never carries the value. BCrypt reads at most 72 bytes.
     */
    private static String requireStrongPassword(String password) {
        boolean tooShort = password.length() < MIN_PASSWORD_LENGTH;
        boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;

        if (tooShort || tooLong || !PASSWORD_CLASSES.matcher(password).matches()) {
            throw new IllegalStateException((
                    "app.seed.password (SEED_PASSWORD) must be at least %d characters and at most %d bytes, "
                            + "with a lowercase letter, an uppercase letter and a digit.")
                    .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_BYTES));
        }

        return password;
    }

    private List<User> newUsers() {
        // One hash for all of them: the password is shared, and bcrypt is slow on purpose.
        String encodedPassword = passwordEncoder.encode(password);

        return USERNAMES
                .stream()
                .map(username -> User.builder()
                        .bio(BIO)
                        .username(username)
                        .password(encodedPassword)
                        .email(username.toLowerCase(Locale.ROOT) + "@" + EMAIL_DOMAIN)
                        .enabled(true)
                        .build())
                .toList();
    }

    /**
     * Goes through the follow service, so counts, outbox events and the timeline back-fill behave as they
     * do for real follows. The fixed random seed gives the same graph on every fresh database.
     */
    private int seedFollows(List<User> users) {
        Random random = new Random(RANDOM_SEED);
        List<User[]> pairs = new ArrayList<>();

        for (User follower : users) {
            for (User target : users) {
                if (follower != target && random.nextDouble() < FOLLOW_PROBABILITY) {
                    pairs.add(new User[]{follower, target});
                }
            }
        }

        pairs.forEach(pair -> followService.follow(pair[0].getId(), pair[1].getUsername()));

        return pairs.size();
    }
}
