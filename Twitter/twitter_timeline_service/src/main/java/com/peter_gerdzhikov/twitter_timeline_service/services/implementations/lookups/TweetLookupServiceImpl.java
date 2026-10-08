package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetPageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetSummaryClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream.RestClientConfiguration;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TweetLookupServiceImpl extends DownstreamLookupSupport implements TweetLookupService {

    private static final String TWEETS_PATH = "/internal/v1/tweets?ids={ids}";

    private static final String BY_AUTHOR_PATH = "/internal/v1/tweets/by-author/{authorId}?since={since}&limit={limit}";

    private static final String AUTHOR_PAGE_PATH = "/internal/v1/tweets/by-author/{authorId}/page?size={size}";

    private static final String AUTHOR_PAGE_AFTER_PATH = AUTHOR_PAGE_PATH + "&cursor={cursor}";

    private final RestClient tweetServiceRestClient;

    public TweetLookupServiceImpl(
            @Qualifier(RestClientConfiguration.TWEET_SERVICE_REST_CLIENT) RestClient tweetServiceRestClient
    ) {
        super("tweet service");
        this.tweetServiceRestClient = tweetServiceRestClient;
    }

    @Override
    public Map<UUID, TweetClientDTO> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<TweetClientDTO> tweets = execute(() -> tweetServiceRestClient
                .get()
                .uri(TWEETS_PATH, commaSeparated(ids))
                .retrieve()
                .body(new ParameterizedTypeReference<List<TweetClientDTO>>() {
                }));

        requireWellFormed(tweets);

        return tweets
                .stream()
                .collect(Collectors.toMap(TweetClientDTO::getId, Function.identity(), (first, second) -> first));
    }

    @Override
    public List<TweetSummaryClientDTO> findNewestByAuthor(UUID authorId, Instant since, int limit) {
        return execute(() -> tweetServiceRestClient
                .get()
                .uri(BY_AUTHOR_PATH, authorId, since.toString(), limit)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TweetSummaryClientDTO>>() {
                }));
    }

    /**
     * The size and the author id are valid by now, so a {@code 400} from the tweet service can only be the
     * cursor, which only it can read.
     */
    @Override
    public TweetPageClientDTO findPageByAuthor(UUID authorId, String cursor, int size) {
        TweetPageClientDTO page = execute(() -> tweetServiceRestClient
                .get()
                .uri(cursor == null ? AUTHOR_PAGE_PATH : AUTHOR_PAGE_AFTER_PATH, pageUriVariables(authorId, cursor, size))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(), (request, response) -> {
                    throw new InvalidCursorException();
                })
                .body(TweetPageClientDTO.class));

        requireWellFormed(page == null ? null : page.getItems());

        return page;
    }

    private Map<String, Object> pageUriVariables(UUID authorId, String cursor, int size) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("authorId", authorId);
        variables.put("size", size);
        if (cursor != null) {
            variables.put("cursor", cursor);
        }

        return variables;
    }

    private void requireWellFormed(List<TweetClientDTO> tweets) {
        boolean isMalformed = tweets == null || tweets
                .stream()
                .anyMatch(tweet -> tweet == null || tweet.getId() == null || tweet.getAuthorId() == null || tweet.getImages() == null);

        if (isMalformed) {
            log.warn("The tweet service answered with a tweet missing its id, author or images.");
            throw new UpstreamUnavailableException();
        }
    }
}
